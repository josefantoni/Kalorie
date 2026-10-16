package antoni.kalorie.core.tips

import android.content.Context
import antoni.kalorie.core.auth.CurrentActivityProvider
import antoni.kalorie.core.models.TipProductDomain
import antoni.kalorie.core.models.TipPurchaseOutcome
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class TipStoreError : Exception() {
    data object NoPresentingActivity : TipStoreError()
    data object ProductUnavailable : TipStoreError()
    data class BillingFailure(val responseCode: Int) : TipStoreError()
}

interface TipStoreProtocol {
    suspend fun products(): List<TipProductDomain>
    suspend fun purchase(productId: String): TipPurchaseOutcome
    fun start() = Unit
}

class PlayBillingTipStore(
    context: Context,
    private val currentActivityProvider: CurrentActivityProvider,
) : TipStoreProtocol,
    PurchasesUpdatedListener {

    // MARK: - Properties

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private val connectionMutex = Mutex()
    private var pendingOutcome: CompletableDeferred<TipPurchaseOutcome>? = null
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    // MARK: - Functions

    override suspend fun products(): List<TipProductDomain> {
        connect()
        consumeUnfinishedPurchases()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Constants.Tips.productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw TipStoreError.BillingFailure(result.billingResult.responseCode)
        }
        val details = result.productDetailsList.orEmpty()
        productDetailsById.putAll(details.associateBy { it.productId })
        return Constants.Tips.productIds.mapNotNull { id ->
            val price = productDetailsById[id]?.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
            TipProductDomain(id = id, displayPrice = price)
        }
    }

    override suspend fun purchase(productId: String): TipPurchaseOutcome {
        connect()
        val details = productDetailsById[productId] ?: throw TipStoreError.ProductUnavailable
        val activity = currentActivityProvider.activity ?: throw TipStoreError.NoPresentingActivity
        val outcome = CompletableDeferred<TipPurchaseOutcome>()
        pendingOutcome = outcome
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        val launchResult = client.launchBillingFlow(activity, params)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingOutcome = null
            throw TipStoreError.BillingFailure(launchResult.responseCode)
        }
        return outcome.await()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val outcome = pendingOutcome
        pendingOutcome = null
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                try {
                    var result = TipPurchaseOutcome.CANCELLED
                    purchases.orEmpty().forEach { purchase ->
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                consume(purchase)
                                result = TipPurchaseOutcome.PURCHASED
                            }
                            Purchase.PurchaseState.PENDING -> result = TipPurchaseOutcome.PENDING
                            else -> Unit
                        }
                    }
                    outcome?.complete(result)
                } catch (error: CancellationException) {
                    outcome?.cancel()
                    throw error
                } catch (error: Exception) {
                    Log.error(error, Constants.LogCategory.SUPPORT)
                    outcome?.completeExceptionally(error)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> outcome?.complete(TipPurchaseOutcome.CANCELLED)
            else -> outcome?.completeExceptionally(TipStoreError.BillingFailure(billingResult.responseCode))
        }
    }

    override fun start() {
        scope.launch {
            try {
                connect()
                consumeUnfinishedPurchases()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.SUPPORT)
            }
        }
    }

    // MARK: - Private

    private suspend fun connect() = connectionMutex.withLock {
        if (client.isReady) return@withLock
        suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (!continuation.isActive) return
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(TipStoreError.BillingFailure(billingResult.responseCode))
                        }
                    }

                    override fun onBillingServiceDisconnected() = Unit
                },
            )
        }
    }

    private suspend fun consumeUnfinishedPurchases() {
        try {
            val result = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            )
            result.purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { consume(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.SUPPORT)
        }
    }

    private suspend fun consume(purchase: Purchase) {
        val result = client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw TipStoreError.BillingFailure(result.billingResult.responseCode)
        }
    }
}
