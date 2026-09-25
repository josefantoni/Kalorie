package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.epochSecondsAsExactDouble
import antoni.kalorie.core.utils.isFirestorePermissionDenied
import kotlinx.coroutines.CancellationException

sealed class RejectSubmissionError : Exception() {
    data object ReasonRequired : RejectSubmissionError()
    data object AlreadyResolved : RejectSubmissionError()
    data object ChangedSinceReview : RejectSubmissionError()
}

interface RejectSubmissionUseCaseProtocol {
    suspend operator fun invoke(submission: FoodItemSubmissionDomain, reason: String)
}

class RejectSubmissionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : RejectSubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, reason: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) throw RejectSubmissionError.ReasonRequired
        val dto = FoodItemSubmissionDTO(
            id = submission.id,
            barcode = submission.barcode,
            submittedBy = submission.submittedBy,
            status = FoodItemSubmissionStatus.REJECTED,
            submittedAt = submission.submittedAt,
            rejectReason = trimmedReason,
            item = submission.item,
        )
        try {
            dataProvider.setAsync(dto, id = submission.id, inCollection = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
        } catch (writeError: CancellationException) {
            throw writeError
        } catch (writeError: Exception) {
            if (!writeError.isFirestorePermissionDenied) throw writeError
            // The rules deny "already resolved", "resubmitted since review" (submitted_at must be
            // unchanged) and an expired session with the same code, so re-read to tell them apart.
            val current: FoodItemSubmissionDTO? = try {
                dataProvider.loadFromServerAsync(id = submission.id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
            } catch (_: CancellationException) {
                throw writeError
            } catch (_: Exception) {
                throw writeError
            }
            if (current == null) throw RejectSubmissionError.AlreadyResolved
            if (current.submittedAt != submission.submittedAt.epochSecondsAsExactDouble()) throw RejectSubmissionError.ChangedSinceReview
            throw writeError
        }
    }
}
