package antoni.kalorie.features.mealtypesheet

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.export.ExportConfigurator

class MealTypeSheetRouter(
    private val exportConfigurator: ExportConfigurator = ExportConfigurator(),
) {

    // MARK: - Functions

    @Composable
    fun makeExportView(mealTypes: List<MealTypeDomain>, onBack: () -> Unit) {
        exportConfigurator.createView(mealTypes = mealTypes, onBack = onBack)
    }
}
