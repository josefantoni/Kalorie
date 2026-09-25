package antoni.kalorie.core.models

sealed class FoodItemValidationError : Exception() {
    data object InvalidCode : FoodItemValidationError()
    data object InvalidName : FoodItemValidationError()
    data object InvalidCalories : FoodItemValidationError()
    data object InvalidWeight : FoodItemValidationError()
    data class InvalidPortion(val error: FoodPortionError) : FoodItemValidationError()

    // MARK: - Functions

    fun <T> mapped(
        invalidCode: T,
        invalidName: T,
        invalidCalories: T,
        invalidWeight: T,
        invalidPortion: (FoodPortionError) -> T,
    ): T = when (this) {
        InvalidCode -> invalidCode
        InvalidName -> invalidName
        InvalidCalories -> invalidCalories
        InvalidWeight -> invalidWeight
        is InvalidPortion -> invalidPortion(error)
    }
}

object FoodItemValidation {

    // MARK: - Properties

    private val barcodeLengths = setOf(8, 12, 13)
    private val submissionUUIDPattern = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")

    // MARK: - Functions

    fun isValidBarcode(id: String): Boolean = id.all { it in '0'..'9' } && id.length in barcodeLengths

    fun isValidSubmissionUUID(id: String): Boolean = submissionUUIDPattern.matches(id)

    fun validate(item: FoodItemDomain): FoodItemValidationError? {
        if (!isValidBarcode(item.id) && !isValidSubmissionUUID(item.id)) return FoodItemValidationError.InvalidCode
        if (item.czName.isEmpty()) return FoodItemValidationError.InvalidName
        if (item.caloriesPerHundredGrams <= 0) return FoodItemValidationError.InvalidCalories
        if (item.weight <= 0) return FoodItemValidationError.InvalidWeight
        for (portion in item.portions) {
            FoodPortionValidation.validate(portion.name, portion.grams)?.let {
                return FoodItemValidationError.InvalidPortion(it)
            }
        }
        FoodPortionValidation.validate(item.portions)?.let { return FoodItemValidationError.InvalidPortion(it) }
        return null
    }
}
