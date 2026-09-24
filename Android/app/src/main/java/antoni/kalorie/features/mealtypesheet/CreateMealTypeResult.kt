package antoni.kalorie.features.mealtypesheet

sealed class CreateMealTypeError : Exception() {
    data object EmptyName : CreateMealTypeError()
    data object DuplicateName : CreateMealTypeError()
    data object TimeConflict : CreateMealTypeError()
    data object DurationTooShort : CreateMealTypeError()
}
