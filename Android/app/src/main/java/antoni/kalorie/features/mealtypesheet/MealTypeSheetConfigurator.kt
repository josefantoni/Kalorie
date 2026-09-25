package antoni.kalorie.features.mealtypesheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.CreateMealTypeUseCase
import antoni.kalorie.core.usecases.DeleteMealTypeUseCase
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCase
import java.util.UUID

class MealTypeSheetConfigurator(
    private val router: MealTypeSheetRouter = MealTypeSheetRouter(),
) {

    // MARK: - Functions

    @Composable
    fun createView(mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onMealTypesChanged: () -> Unit = {}) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
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
