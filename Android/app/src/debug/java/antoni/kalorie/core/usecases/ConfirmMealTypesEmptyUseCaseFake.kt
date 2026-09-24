package antoni.kalorie.core.usecases

data class ConfirmMealTypesEmptyUseCaseFake(
    val stubbedResult: Boolean = false,
    val stubbedError: Exception? = null,
) : ConfirmMealTypesEmptyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean {
        stubbedError?.let { throw it }
        return stubbedResult
    }
}
