package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.epochSecondsAsDouble
import kotlinx.coroutines.CancellationException

sealed class ApproveSubmissionError : Exception() {
    data object AlreadyResolved : ApproveSubmissionError()
    data object ChangedSinceReview : ApproveSubmissionError()
}

interface ApproveSubmissionUseCaseProtocol {
    suspend operator fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain)
}

class ApproveSubmissionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val createFoodItem: CreateFoodItemUseCaseProtocol,
) : ApproveSubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val current: FoodItemSubmissionDTO = dataProvider.loadFromServerAsync(
            id = submission.id,
            from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
        ) ?: throw ApproveSubmissionError.AlreadyResolved
        if (current.submittedAt != submission.submittedAt.epochSecondsAsDouble()) throw ApproveSubmissionError.ChangedSinceReview
        createFoodItem(item)
        try {
            dataProvider.deleteAsync(id = submission.id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The catalogue write already succeeded. A submission that survives this failed delete
            // resurfaces as a collision in the queue and is cleared by hand (design 0009, Risks).
            Log.error(error, Constants.LogCategory.MODERATION)
        }
    }
}
