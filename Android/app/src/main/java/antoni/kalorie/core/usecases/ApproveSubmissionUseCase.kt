package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.epochSecondsAsExactDouble
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed class ApproveSubmissionError : Exception() {
    data object AlreadyResolved : ApproveSubmissionError()
    data object ChangedSinceReview : ApproveSubmissionError()
    data object PhotoMissing : ApproveSubmissionError()
}

interface ApproveSubmissionUseCaseProtocol {
    suspend operator fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain, photo: FoodItemFormPhoto)
}

class ApproveSubmissionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val createFoodItem: CreateFoodItemUseCaseProtocol,
    private val downloadFoodPhoto: DownloadFoodPhotoUseCaseProtocol,
    private val uploadFoodPhoto: UploadFoodPhotoUseCaseProtocol,
    private val deleteFoodPhoto: DeleteFoodPhotoUseCaseProtocol,
) : ApproveSubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain, photo: FoodItemFormPhoto) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        if (photo == FoodItemFormPhoto.None) throw ApproveSubmissionError.PhotoMissing
        // The catalogue gets its own copy: the author keeps delete rights on their submission folder,
        // so pointing the catalogue at that file would let them remove a photo every user sees.
        val photoData = bytesOf(photo)
        val catalogueUrl = uploadFoodPhoto(photoData, "${Constants.Storage.CATALOG_PHOTOS_FOLDER}/${item.id}")
        try {
            // Re-read after the slow photo transfer so an edit the author resubmitted in the
            // meantime is never overwritten by the maintainer's stale form values.
            val current: FoodItemSubmissionDTO = dataProvider.loadFromServerAsync(
                id = submission.id,
                from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            ) ?: throw ApproveSubmissionError.AlreadyResolved
            if (current.submittedAt != submission.submittedAt.epochSecondsAsExactDouble()) throw ApproveSubmissionError.ChangedSinceReview
            createFoodItem(item.withPhotoUrl(catalogueUrl))
        } catch (error: Exception) {
            withContext(NonCancellable) { deleteFoodPhoto.deleteQuietly(catalogueUrl) }
            throw error
        }
        try {
            dataProvider.deleteAsync(id = submission.id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
            submission.item.photoUrl?.let { deleteFoodPhoto.deleteQuietly(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The catalogue write already succeeded. A submission that survives this failed delete
            // resurfaces as a collision in the queue and is cleared by hand (design 0009, Risks).
            Log.error(error, Constants.LogCategory.MODERATION)
        }
    }

    // MARK: - Private

    private suspend fun bytesOf(photo: FoodItemFormPhoto): ByteArray = when (photo) {
        is FoodItemFormPhoto.Local -> photo.data
        is FoodItemFormPhoto.Remote -> downloadFoodPhoto(photo.url)
        FoodItemFormPhoto.None -> throw ApproveSubmissionError.PhotoMissing
    }
}
