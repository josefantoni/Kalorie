package antoni.kalorie.features.settings

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.export.ExportConfigurator
import antoni.kalorie.features.moderation.ModerationConfigurator
import antoni.kalorie.features.support.SupportConfigurator

class SettingsRouter(
    private val exportConfigurator: ExportConfigurator = ExportConfigurator(),
    private val moderationConfigurator: ModerationConfigurator,
    private val supportConfigurator: SupportConfigurator = SupportConfigurator(),
) {

    // MARK: - Functions

    @Composable
    fun makeExportView(mealTypes: List<MealTypeDomain>, onBack: () -> Unit) {
        exportConfigurator.createView(mealTypes = mealTypes, onBack = onBack)
    }

    @Composable
    fun makeSupportView(onBack: () -> Unit) {
        supportConfigurator.createView(onBack = onBack)
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
