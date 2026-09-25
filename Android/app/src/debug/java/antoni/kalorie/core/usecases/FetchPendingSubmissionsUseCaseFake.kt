package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemSubmissionDomain

data class FetchPendingSubmissionsUseCaseFake(
    val stubbedSubmissions: List<FoodItemSubmissionDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemSubmissionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSubmissions
    }
}
