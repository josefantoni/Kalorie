package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain

data class ApproveSubmissionUseCaseFake(
    val errorToThrow: Exception? = null,
) : ApproveSubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain) {
        errorToThrow?.let { throw it }
    }
}
