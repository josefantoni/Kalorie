package antoni.kalorie.features.settings

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.export.ExportConfigurator
import antoni.kalorie.features.moderation.ModerationConfigurator

class SettingsRouter(
    private val exportConfigurator: ExportConfigurator = ExportConfigurator(),
    private val moderationConfigurator: ModerationConfigurator,
) {

    // MARK: - Functions

    @Composable
    fun makeExportView(mealTypes: List<MealTypeDomain>, onBack: () -> Unit) {
        exportConfigurator.createView(mealTypes = mealTypes, onBack = onBack)
    }

    @Composable
    fun makeModerationQueueView(onDismiss: () -> Unit) {
        moderationConfigurator.createView(onDismiss = onDismiss)
    }

    @Composable
    fun makeModerationReportsView(onDismiss: () -> Unit) {
        moderationConfigurator.createReportsView(onDismiss = onDismiss)
    }
}
