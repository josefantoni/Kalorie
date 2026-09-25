package antoni.kalorie.features.mealtypesheet

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.CreateMealTypeUseCase
import antoni.kalorie.core.usecases.DeleteMealTypeUseCase
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCase

class MealTypeSheetConfigurator(
    private val router: MealTypeSheetRouter = MealTypeSheetRouter(),
) {

    // MARK: - Functions

    @Composable
    fun createView(mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onMealTypesChanged: () -> Unit = {}) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            val dataProvider = FirestoreDataProvider()
            val authProvider = AuthProvider()
            MealTypeSheetViewModel(
                mealTypes = mealTypes,
                onMealTypesChanged = onMealTypesChanged,
                createMealType = CreateMealTypeUseCase(dataProvider, authProvider),
                deleteMealType = DeleteMealTypeUseCase(dataProvider, authProvider),
                updateMealTypeTimes = UpdateMealTypeTimesUseCase(dataProvider, authProvider),
            )
        }
        MealTypeSheetView(viewModel = viewModel, router = router, onDismiss = onDismiss)
    }
}
