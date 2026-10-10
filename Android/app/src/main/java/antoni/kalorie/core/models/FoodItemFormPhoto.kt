package antoni.kalorie.core.models

sealed interface FoodItemFormPhoto {
    data object None : FoodItemFormPhoto
    data class Remote(val url: String) : FoodItemFormPhoto
    data class Local(val data: ByteArray) : FoodItemFormPhoto {

        // MARK: - Functions

        override fun equals(other: Any?): Boolean = other is Local && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }
}
