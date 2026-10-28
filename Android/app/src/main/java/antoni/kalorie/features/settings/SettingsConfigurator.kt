package antoni.kalorie.features.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.CreateMealTypeUseCase
import antoni.kalorie.core.usecases.DeleteMealTypeUseCase
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCase
import antoni.kalorie.core.usecases.MaintainerClaimCache
import antoni.kalorie.core.usecases.SaveAppSettingsUseCase
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCase
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner
import antoni.kalorie.features.moderation.ModerationConfigurator

class SettingsConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Properties

    private val maintainerClaimCache = MaintainerClaimCache()
    private val router = SettingsRouter(moderationConfigurator = ModerationConfigurator(dataProvider, authProvider))

    // MARK: - Functions

    @Composable
    fun createView(
        mealTypes: List<MealTypeDomain>,
        onDismiss: () -> Unit,
        appSettings: AppSettingsDomain = AppSettingsDomain.defaults,
        hasLoadedAppSettings: Boolean = true,
        onMealTypesChanged: () -> Unit = {},
        onAppSettingsChanged: (AppSettingsDomain) -> Unit = {},
    ) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            SettingsViewModel(
                mealTypes = mealTypes,
                appSettings = appSettings,
                onMealTypesChanged = onMealTypesChanged,
                onAppSettingsChanged = onAppSettingsChanged,
                saveAppSettings = SaveAppSettingsUseCase(dataProvider, authProvider),
                createMealType = CreateMealTypeUseCase(dataProvider, authProvider),
                deleteMealType = DeleteMealTypeUseCase(dataProvider, authProvider),
                updateMealTypeTimes = UpdateMealTypeTimesUseCase(dataProvider, authProvider),
                fetchMaintainerClaim = FetchMaintainerClaimUseCase(maintainerClaimCache),
                hasLoadedAppSettings = hasLoadedAppSettings,
            )
        }
        SettingsView(viewModel = viewModel, router = router, onDismiss = onDismiss)
    }
}
