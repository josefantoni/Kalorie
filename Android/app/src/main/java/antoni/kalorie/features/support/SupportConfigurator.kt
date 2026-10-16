package antoni.kalorie.features.support

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.KalorieApplication
import antoni.kalorie.core.usecases.LoadTipProductsUseCase
import antoni.kalorie.core.usecases.PurchaseTipUseCase
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner

class SupportConfigurator {

    // MARK: - Functions

    @Composable
    fun createView(onBack: () -> Unit) {
        val application = LocalContext.current.applicationContext as KalorieApplication
        val store = application.tipStore
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            SupportViewModel(
                loadTipProducts = LoadTipProductsUseCase(store),
                purchaseTip = PurchaseTipUseCase(store),
            )
        }
        SupportView(viewModel = viewModel, onBack = onBack)
    }
}
