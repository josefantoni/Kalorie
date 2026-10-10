package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.FoodItemValidation
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import java.time.Instant

class FoodItemSubmissionWriter(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val uploadFoodPhoto: UploadFoodPhotoUseCaseProtocol,
    private val deleteFoodPhoto: DeleteFoodPhotoUseCaseProtocol,
) {

    // MARK: - Functions

    suspend fun write(
        id: String,
        item: FoodItemDomain,
        photo: FoodItemFormPhoto,
        previousPhotoUrl: String?,
    ): FoodItemSubmissionDomain {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val resolvedItem = if (item.id.isEmpty()) item.withId(id) else item
        FoodItemValidation.validate(resolvedItem)?.let { throw FoodItemSubmissionError.from(it) }
        if (photo == FoodItemFormPhoto.None) throw FoodItemSubmissionError.PhotoMissing
        val existing: FoodItemDTO? = dataProvider.loadFromServerAsync(id = resolvedItem.id, from = Constants.Firestore.FOOD_ITEMS)
        if (existing != null) throw FoodItemSubmissionError.ItemAlreadyExists
        val uploadedUrl = uploadIfLocal(photo, userId)
        val submission = FoodItemSubmissionDomain(
            id = id,
            barcode = resolvedItem.barcode,
            submittedBy = userId,
            status = FoodItemSubmissionStatus.PENDING,
            submittedAt = Instant.now(),
            rejectReason = null,
            item = resolvedItem.withPhotoUrl(uploadedUrl ?: (photo as? FoodItemFormPhoto.Remote)?.url),
        )
        val dto = FoodItemSubmissionDTO(
            id = submission.id,
            barcode = submission.barcode,
            submittedBy = submission.submittedBy,
            status = submission.status,
            submittedAt = submission.submittedAt,
            rejectReason = submission.rejectReason,
            item = submission.item,
        )
        try {
            dataProvider.setAsync(dto, id = submission.id, inCollection = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (uploadedUrl != null) deleteFoodPhoto.deleteQuietly(uploadedUrl)
            throw error
        }
        if (uploadedUrl != null && previousPhotoUrl != null) deleteFoodPhoto.deleteQuietly(previousPhotoUrl)
        return submission
    }

    // MARK: - Private

    private suspend fun uploadIfLocal(photo: FoodItemFormPhoto, userId: String): String? {
        if (photo !is FoodItemFormPhoto.Local) return null
        return try {
            uploadFoodPhoto(photo.data, "${Constants.Storage.SUBMISSION_PHOTOS_FOLDER}/$userId")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.STORAGE)
            throw FoodItemSubmissionError.PhotoUploadFailed
        }
    }
}
