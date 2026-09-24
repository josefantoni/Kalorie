package antoni.kalorie.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FoodItemKind {
    @SerialName("catalogue") CATALOGUE,
    @SerialName("external") EXTERNAL,
    @SerialName("created_meal") CREATED_MEAL,
}
