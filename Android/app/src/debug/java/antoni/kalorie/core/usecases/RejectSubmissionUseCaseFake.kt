package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemSubmissionDomain

data class RejectSubmissionUseCaseFake(
    val errorToThrow: Exception? = null,
) : RejectSubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, reason: String) {
        errorToThrow?.let { throw it }
    }
}
