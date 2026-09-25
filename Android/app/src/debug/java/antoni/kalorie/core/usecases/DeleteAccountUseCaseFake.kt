package antoni.kalorie.core.usecases

data class DeleteAccountUseCaseFake(
    val errorToThrow: Exception? = null,
) : DeleteAccountUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(skipDataWipe: Boolean) {
        errorToThrow?.let { throw it }
    }
}
