package antoni.kalorie.core.networking

import antoni.kalorie.core.models.BodyWeightDomain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BodyWeightDTO(
    val id: String,
    @SerialName("weight_kg") val weightKg: Double,
) {

    // MARK: - Init

    constructor(weight: BodyWeightDomain) : this(id = weight.dayKey, weightKg = weight.kilograms)

    // MARK: - Functions

    fun asDomain(): BodyWeightDomain = BodyWeightDomain(dayKey = id, kilograms = weightKg)
}
