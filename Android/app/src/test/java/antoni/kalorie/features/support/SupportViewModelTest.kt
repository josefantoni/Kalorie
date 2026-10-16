package antoni.kalorie.features.support

import antoni.kalorie.R
import antoni.kalorie.core.models.TipProductDomain
import antoni.kalorie.core.models.TipPurchaseOutcome
import antoni.kalorie.core.usecases.LoadTipProductsUseCaseFake
import antoni.kalorie.core.usecases.LoadTipProductsUseCaseProtocol
import antoni.kalorie.core.usecases.PurchaseTipUseCaseFake
import antoni.kalorie.core.usecases.PurchaseTipUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportViewModelTest {

    private val small = TipProductDomain(id = Constants.Tips.SMALL_PRODUCT_ID, displayPrice = "29 Kč")

    @Test
    fun onAppear_loadsProducts() = runTest {
        val sut = makeSUT(loadTipProducts = LoadTipProductsUseCaseFake(stubbedProducts = listOf(small)))

        sut.onAppear()

        assertEquals(listOf(small), sut.products.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenLoadingFails_showsRetryableAlert() = runTest {
        val sut = makeSUT(loadTipProducts = LoadTipProductsUseCaseFake(stubbedError = IllegalStateException()))

        sut.onAppear()

        assertTrue(sut.products.value.isEmpty())
        assertEquals(R.string.support_error_productsUnavailable, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onAppear_whenNoProductsReturned_showsAlert() = runTest {
        val sut = makeSUT()

        sut.onAppear()

        assertEquals(R.string.support_error_productsUnavailable, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onProductTapped_whenPurchased_showsThankYouWithoutAlertAndKeepsItUntilDismissed() = runTest {
        val sut = makeSUT(purchaseTip = PurchaseTipUseCaseFake(stubbedOutcome = TipPurchaseOutcome.PURCHASED))

        sut.onProductTapped(small)

        assertTrue(sut.isThankYouVisible.value)
        assertNull(sut.alertItem.value)
        assertEquals(SupportViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onThankYouDismissed_hidesThankYou() = runTest {
        val sut = makeSUT()
        sut.onProductTapped(small)

        sut.onThankYouDismissed()

        assertFalse(sut.isThankYouVisible.value)
    }

    @Test
    fun onProductTapped_whenMediumTipPurchased_choosesLunchAnimation() = runTest {
        val sut = makeSUT()

        sut.onProductTapped(TipProductDomain(Constants.Tips.MEDIUM_PRODUCT_ID, "79 Kč"))

        assertEquals(SupportViewModel.ThankYouAnimation.LUNCH, sut.thankYouAnimation.value)
    }

    @Test
    fun onProductTapped_whenLargeTipPurchased_choosesGrandmaAnimation() = runTest {
        val sut = makeSUT()

        sut.onProductTapped(TipProductDomain(Constants.Tips.LARGE_PRODUCT_ID, "199 Kč"))

        assertEquals(SupportViewModel.ThankYouAnimation.GRANDMA, sut.thankYouAnimation.value)
    }

    @Test
    fun onProductTapped_whenSmallTipPurchased_choosesBaguetteAnimation() = runTest {
        val sut = makeSUT()

        sut.onProductTapped(small)

        assertEquals(SupportViewModel.ThankYouAnimation.BAGUETTES, sut.thankYouAnimation.value)
    }

    @Test
    fun onProductTapped_whenCancelled_showsNothing() = runTest {
        val sut = makeSUT(purchaseTip = PurchaseTipUseCaseFake(stubbedOutcome = TipPurchaseOutcome.CANCELLED))

        sut.onProductTapped(small)

        assertNull(sut.alertItem.value)
    }

    @Test
    fun onProductTapped_whenPending_showsPendingMessage() = runTest {
        val sut = makeSUT(purchaseTip = PurchaseTipUseCaseFake(stubbedOutcome = TipPurchaseOutcome.PENDING))

        sut.onProductTapped(small)

        assertEquals(R.string.support_pending_message, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onProductTapped_whenPurchaseFails_showsError() = runTest {
        val sut = makeSUT(purchaseTip = PurchaseTipUseCaseFake(stubbedError = IllegalStateException()))

        sut.onProductTapped(small)

        assertEquals(R.string.support_error_purchaseFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onProductTapped_whileThankYouIsVisible_ignoresTheTap() = runTest {
        val sut = makeSUT()
        sut.onProductTapped(small)

        sut.onProductTapped(TipProductDomain(id = Constants.Tips.LARGE_PRODUCT_ID, displayPrice = "199 Kč"))

        assertEquals(SupportViewModel.ThankYouAnimation.BAGUETTES, sut.thankYouAnimation.value)
    }

    private fun makeSUT(
        loadTipProducts: LoadTipProductsUseCaseProtocol = LoadTipProductsUseCaseFake(),
        purchaseTip: PurchaseTipUseCaseProtocol = PurchaseTipUseCaseFake(),
    ) = SupportViewModel(loadTipProducts = loadTipProducts, purchaseTip = purchaseTip)
}
