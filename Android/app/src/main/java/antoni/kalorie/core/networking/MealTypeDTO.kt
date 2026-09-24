package antoni.kalorie.core.networking

import kotlinx.serialization.Serializable

@Serializable
data class MealTypeDTO(
    val id: String,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
)
