package antoni.kalorie.features.mycreatedmeal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.CreateMyCreatedMealUseCase
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemsByIdsUseCase
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCase
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCase
import java.util.UUID

class MyCreatedMealEditorConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(
        existingMeal: MyCreatedMealDomain? = null,
        onSaved: () -> Unit = {},
        dismissesOnSave: Boolean = true,
        onDismiss: () -> Unit,
        navigationIcon: @Composable () -> Unit,
        header: @Composable () -> Unit = {},
    ) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            MyCreatedMealEditorViewModel(
                searchFoodItems = SearchFoodItemsUseCase(dataProvider),
                searchFoodExternally = SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCase(),
                fetchFoodItemsByIds = FetchFoodItemsByIdsUseCase(dataProvider),
                createMyCreatedMeal = CreateMyCreatedMealUseCase(dataProvider, authProvider),
                updateMyCreatedMeal = UpdateMyCreatedMealUseCase(dataProvider, authProvider),
                existingMeal = existingMeal,
                onSaved = onSaved,
                dismissesOnSave = dismissesOnSave,
            )
        }
        MyCreatedMealEditorView(viewModel = viewModel, onDismiss = onDismiss, navigationIcon = navigationIcon, header = header)
    }
}
