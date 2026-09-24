package antoni.kalorie.core.models

import androidx.annotation.StringRes
import antoni.kalorie.R

data class FoodPortionDomain(
    val name: String,
    val grams: Double,
)

sealed class FoodPortionError : Exception() {
    data object InvalidName : FoodPortionError()
    data object InvalidGrams : FoodPortionError()
    data object TooMany : FoodPortionError()

    // MARK: - Properties

    @get:StringRes
    val alertTitleRes: Int
        get() = when (this) {
            InvalidName -> R.string.foodPortion_error_invalidName
            InvalidGrams -> R.string.foodPortion_error_invalidGrams
            TooMany -> R.string.foodPortion_error_tooMany
        }
}

object FoodPortionValidation {

    // MARK: - Properties

    const val MAX_PORTIONS = 20

    // MARK: - Functions

    fun validate(name: String, grams: Double): FoodPortionError? {
        if (name.isBlank()) return FoodPortionError.InvalidName
        if (grams < 1) return FoodPortionError.InvalidGrams
        return null
    }

    fun validate(portions: List<FoodPortionDomain>): FoodPortionError? =
        if (portions.size > MAX_PORTIONS) FoodPortionError.TooMany else null
}
