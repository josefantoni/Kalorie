package antoni.kalorie.core.networking

import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsDTO(
    @SerialName("show_body_weight") val showBodyWeight: Boolean? = null,
    @SerialName("body_weight_unit") val bodyWeightUnit: String? = null,
    @SerialName("show_daily_goal") val showDailyGoal: Boolean? = null,
    @SerialName("show_goal_percent") val showGoalPercent: Boolean? = null,
    @SerialName("show_goal_bars") val showGoalBars: Boolean? = null,
) {

    // MARK: - Init

    constructor(settings: AppSettingsDomain) : this(
        showBodyWeight = settings.showBodyWeight,
        bodyWeightUnit = settings.bodyWeightUnit.rawValue,
        showDailyGoal = settings.showDailyGoal,
        showGoalPercent = settings.showGoalPercent,
        showGoalBars = settings.showGoalBars,
    )

    // MARK: - Functions

    fun asDomain(): AppSettingsDomain = AppSettingsDomain(
        showBodyWeight = showBodyWeight ?: AppSettingsDomain.defaults.showBodyWeight,
        bodyWeightUnit = bodyWeightUnit?.let(BodyWeightUnit::fromRawValue) ?: AppSettingsDomain.defaults.bodyWeightUnit,
        showDailyGoal = showDailyGoal ?: AppSettingsDomain.defaults.showDailyGoal,
        showGoalPercent = showGoalPercent ?: AppSettingsDomain.defaults.showGoalPercent,
        showGoalBars = showGoalBars ?: AppSettingsDomain.defaults.showGoalBars,
    )
}
