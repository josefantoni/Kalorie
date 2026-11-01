package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.account.AccountConfigurator
import antoni.kalorie.features.addfoodsheet.AddFoodSheetConfigurator
import antoni.kalorie.features.settings.SettingsConfigurator
import java.time.Instant

class DashboardRouter(
    private val accountConfigurator: AccountConfigurator,
    private val settingsConfigurator: SettingsConfigurator,
    private val addFoodSheetConfigurator: AddFoodSheetConfigurator,
    private val foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator,
    private val bodyWeightHistoryConfigurator: BodyWeightHistoryConfigurator,
) {

    // MARK: - Functions

    @Composable
    fun makeAccountView(onDismiss: () -> Unit) {
        accountConfigurator.createView(onDismiss = onDismiss)
    }

    @Composable
    fun makeSettingsView(
        mealTypes: List<MealTypeDomain>,
        appSettings: AppSettingsDomain,
        hasLoadedAppSettings: Boolean,
        onDismiss: () -> Unit,
        onMealTypesChanged: () -> Unit = {},
        onAppSettingsChanged: (AppSettingsDomain) -> Unit = {},
    ) {
        settingsConfigurator.createView(
            mealTypes = mealTypes,
            onDismiss = onDismiss,
            appSettings = appSettings,
            hasLoadedAppSettings = hasLoadedAppSettings,
            onMealTypesChanged = onMealTypesChanged,
            onAppSettingsChanged = onAppSettingsChanged,
        )
    }

    @Composable
    fun makeAddFoodSheetView(date: Instant, mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onFoodSaved: () -> Unit = {}) {
        addFoodSheetConfigurator.createView(date = date, mealTypes = mealTypes, onDismiss = onDismiss, onFoodSaved = onFoodSaved)
    }

    @Composable
    fun makeFoodConsumedDetailView(
        food: FoodConsumedDomain,
        mealTypes: List<MealTypeDomain>,
        onBack: () -> Unit,
        onFoodUpdated: () -> Unit = {},
    ) {
        foodConsumedDetailConfigurator.createView(food = food, mealTypes = mealTypes, onBack = onBack, onFoodUpdated = onFoodUpdated)
    }

    @Composable
    fun makeBodyWeightHistoryView(unit: BodyWeightUnit, selectedDay: Instant, onBack: () -> Unit, onChanged: () -> Unit) {
        bodyWeightHistoryConfigurator.createView(unit = unit, selectedDay = selectedDay, onBack = onBack, onChanged = onChanged)
    }
}
