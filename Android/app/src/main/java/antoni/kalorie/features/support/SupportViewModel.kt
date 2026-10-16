package antoni.kalorie.features.support

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.TipProductDomain
import antoni.kalorie.core.models.TipPurchaseOutcome
import antoni.kalorie.core.usecases.LoadTipProductsUseCaseProtocol
import antoni.kalorie.core.usecases.PurchaseTipUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SupportViewModel(
    private val loadTipProducts: LoadTipProductsUseCaseProtocol,
    private val purchaseTip: PurchaseTipUseCaseProtocol,
) : ViewModel() {

    // MARK: - State

    enum class State {
        IDLE,
        LOADING,
        PURCHASING,
    }

    enum class ThankYouAnimation {
        BAGUETTES,
        LUNCH,
        GRANDMA,
    }

    // MARK: - Properties

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state
    private val _products = MutableStateFlow<List<TipProductDomain>>(emptyList())
    val products: StateFlow<List<TipProductDomain>> = _products
    private val _isThankYouVisible = MutableStateFlow(false)
    val isThankYouVisible: StateFlow<Boolean> = _isThankYouVisible
    private val _thankYouAnimation = MutableStateFlow(ThankYouAnimation.BAGUETTES)
    val thankYouAnimation: StateFlow<ThankYouAnimation> = _thankYouAnimation
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onAppear() {
        if (_products.value.isNotEmpty()) return
        _state.value = State.LOADING
        try {
            _products.value = loadTipProducts()
            if (_products.value.isEmpty()) {
                alertItem.value = AlertItem(titleRes = R.string.support_error_productsUnavailable)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.SUPPORT)
            alertItem.value = AlertItem(titleRes = R.string.support_error_productsUnavailable)
        } finally {
            _state.value = State.IDLE
        }
    }

    fun onThankYouDismissed() {
        _isThankYouVisible.value = false
    }

    suspend fun onProductTapped(product: TipProductDomain) {
        if (_state.value != State.IDLE || _isThankYouVisible.value) return
        _state.value = State.PURCHASING
        try {
            when (purchaseTip(product.id)) {
                TipPurchaseOutcome.PURCHASED -> {
                    _state.value = State.IDLE
                    _thankYouAnimation.value = when (product.id) {
                        Constants.Tips.MEDIUM_PRODUCT_ID -> ThankYouAnimation.LUNCH
                        Constants.Tips.LARGE_PRODUCT_ID -> ThankYouAnimation.GRANDMA
                        else -> ThankYouAnimation.BAGUETTES
                    }
                    _isThankYouVisible.value = true
                }
                TipPurchaseOutcome.PENDING -> alertItem.value = AlertItem(titleRes = R.string.support_pending_message)
                TipPurchaseOutcome.CANCELLED -> Unit
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.SUPPORT)
            alertItem.value = AlertItem(titleRes = R.string.support_error_purchaseFailed)
        } finally {
            _state.value = State.IDLE
        }
    }
}
