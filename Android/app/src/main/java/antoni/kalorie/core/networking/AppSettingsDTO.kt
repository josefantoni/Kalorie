package antoni.kalorie.core.networking

import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsDTO(
    @SerialName("show_body_weight") val showBodyWeight: Boolean? = null,
    @SerialName("body_weight_unit") val bodyWeightUnit: String? = null,
) {

    // MARK: - Init

    constructor(settings: AppSettingsDomain) : this(
        showBodyWeight = settings.showBodyWeight,
        bodyWeightUnit = settings.bodyWeightUnit.rawValue,
    )

    // MARK: - Functions

    fun asDomain(): AppSettingsDomain = AppSettingsDomain(
        showBodyWeight = showBodyWeight ?: AppSettingsDomain.defaults.showBodyWeight,
        bodyWeightUnit = bodyWeightUnit?.let(BodyWeightUnit::fromRawValue) ?: AppSettingsDomain.defaults.bodyWeightUnit,
    )
}
