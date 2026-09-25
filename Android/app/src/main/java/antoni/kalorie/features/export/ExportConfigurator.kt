package antoni.kalorie.features.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCase
import antoni.kalorie.core.usecases.FoodExportReportFactory
import antoni.kalorie.core.usecases.GenerateFoodExportUseCase
import antoni.kalorie.core.utils.ContextStringProvider
import java.io.File
import java.util.UUID

class ExportConfigurator {

    // MARK: - Functions

    @Composable
    fun createView(mealTypes: List<MealTypeDomain>, onBack: () -> Unit) {
        val context = LocalContext.current.applicationContext
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            ExportViewModel(
                mealTypes = mealTypes,
                generateFoodExport = GenerateFoodExportUseCase(
                    fetchFoodsConsumedInRange = FetchFoodsConsumedInRangeUseCase(FirestoreDataProvider(), AuthProvider()),
                    reportFactory = FoodExportReportFactory(strings = ContextStringProvider(context)),
                    directory = File(context.cacheDir, "exports"),
                ),
            )
        }
        ExportView(viewModel = viewModel, onBack = onBack)
    }
}
