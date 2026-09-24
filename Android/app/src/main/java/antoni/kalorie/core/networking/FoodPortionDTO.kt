package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodPortionDomain
import kotlinx.serialization.Serializable

@Serializable
data class FoodPortionDTO(
    val name: String,
    val grams: Double,
) {

    // MARK: - Init

    constructor(portion: FoodPortionDomain) : this(name = portion.name, grams = portion.grams)

    // MARK: - Functions

    fun asDomain(): FoodPortionDomain = FoodPortionDomain(name = name, grams = grams)
}
