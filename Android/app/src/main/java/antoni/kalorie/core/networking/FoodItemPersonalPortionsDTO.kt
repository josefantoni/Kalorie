package antoni.kalorie.core.networking

import kotlinx.serialization.Serializable

@Serializable
data class FoodItemPersonalPortionsDTO(
    val id: String,
    val portions: List<FoodPortionDTO>,
)
