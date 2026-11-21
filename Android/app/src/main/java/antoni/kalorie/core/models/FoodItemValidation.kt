package antoni.kalorie.core.models

import antoni.kalorie.textkit.originalName

sealed class FoodItemValidationError : Exception() {
    data object InvalidCode : FoodItemValidationError()
    data object InvalidName : FoodItemValidationError()
    data object InvalidCalories : FoodItemValidationError()
    data object InvalidAlcoholByVolume : FoodItemValidationError()
    data class InvalidPortion(val error: FoodPortionError) : FoodItemValidationError()

    // MARK: - Functions

    fun <T> mapped(
        invalidCode: T,
        invalidName: T,
        invalidCalories: T,
        invalidAlcoholByVolume: T,
        invalidPortion: (FoodPortionError) -> T,
    ): T = when (this) {
        InvalidCode -> invalidCode
        InvalidName -> invalidName
        InvalidCalories -> invalidCalories
        InvalidAlcoholByVolume -> invalidAlcoholByVolume
        is InvalidPortion -> invalidPortion(error)
    }
}

sealed interface FoodItemRequiredNames {
    data object All : FoodItemRequiredNames
    data class Original(val nameLanguage: String) : FoodItemRequiredNames
}

object FoodItemValidation {

    // MARK: - Properties

    private val barcodeLengths = setOf(8, 12, 13)
    private val submissionUUIDPattern = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")

    // MARK: - Functions

    fun isValidBarcode(id: String): Boolean = id.all { it in '0'..'9' } && id.length in barcodeLengths

    fun isValidSubmissionUUID(id: String): Boolean = submissionUUIDPattern.matches(id)

    fun validate(item: FoodItemDomain, requiredNames: FoodItemRequiredNames): FoodItemValidationError? {
        if (!isValidBarcode(item.id) && !isValidSubmissionUUID(item.id)) return FoodItemValidationError.InvalidCode
        if (!hasRequiredNames(item, requiredNames)) return FoodItemValidationError.InvalidName
        if (item.caloriesPerHundredGrams.isNaN() || item.caloriesPerHundredGrams <= 0) return FoodItemValidationError.InvalidCalories
        val alcoholByVolume = item.alcoholByVolume
        if (alcoholByVolume != null && !(alcoholByVolume > 0 && alcoholByVolume <= 100)) return FoodItemValidationError.InvalidAlcoholByVolume
        for (portion in item.portions) {
            FoodPortionValidation.validate(portion.name, portion.grams)?.let {
                return FoodItemValidationError.InvalidPortion(it)
            }
        }
        FoodPortionValidation.validate(item.portions)?.let { return FoodItemValidationError.InvalidPortion(it) }
        return null
    }

    private fun hasRequiredNames(item: FoodItemDomain, requiredNames: FoodItemRequiredNames): Boolean = when (requiredNames) {
        FoodItemRequiredNames.All -> listOf(item.czName, item.skName, item.engName).all { it.isNotBlank() }
        is FoodItemRequiredNames.Original -> originalName(requiredNames.nameLanguage, item.czName, item.skName, item.engName).isNotEmpty()
    }
}
