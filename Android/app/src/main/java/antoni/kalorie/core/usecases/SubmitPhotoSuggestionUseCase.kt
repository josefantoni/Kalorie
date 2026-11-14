package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.TicketLocale
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.PhotoSuggestionCreateDTO
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.features.notifications.UserNotificationsStoreProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

interface SubmitPhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(itemId: String, photo: ByteArray)
}

class SubmitPhotoSuggestionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val storageProvider: StorageDataProviderProtocol,
    private val notificationsStore: UserNotificationsStoreProtocol,
    private val locale: String = TicketLocale.current(),
) : SubmitPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(itemId: String, photo: ByteArray) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val ticketId = dataProvider.newDocumentId(Constants.Firestore.PHOTO_SUGGESTIONS)
        val path = "${Constants.Storage.PHOTO_SUGGESTIONS_FOLDER}/$userId/$itemId/$ticketId.jpg"
        val photoUrl = storageProvider.uploadAsync(photo, path, Constants.Storage.PHOTO_CONTENT_TYPE)
        val dto = PhotoSuggestionCreateDTO(
            itemId = itemId,
            suggestedBy = userId,
            photoUrl = photoUrl,
            suggestedAt = Instant.now(),
            locale = locale,
        )
        try {
            dataProvider.setAsync(dto, id = ticketId, inCollection = Constants.Firestore.PHOTO_SUGGESTIONS)
        } catch (error: Exception) {
            withContext(NonCancellable) { deleteUploadedFileQuietly(path) }
            throw error
        }
        notificationsStore.mayHaveUnread = true
        notificationsStore.lastSubmittedAt = Instant.now()
    }

    // MARK: - Private

    private suspend fun deleteUploadedFileQuietly(path: String) {
        try {
            storageProvider.deleteAtPathAsync(path)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.STORAGE)
        }
    }
}
