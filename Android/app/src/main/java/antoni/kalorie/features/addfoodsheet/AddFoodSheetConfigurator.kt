package antoni.kalorie.features.addfoodsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import java.util.UUID

class AddFoodSheetConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(onDismiss: () -> Unit) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            AddFoodSheetViewModel(searchFoodItems = SearchFoodItemsUseCase(dataProvider))
        }
        AddFoodSheetView(viewModel = viewModel, onDismiss = onDismiss)
    }
}
