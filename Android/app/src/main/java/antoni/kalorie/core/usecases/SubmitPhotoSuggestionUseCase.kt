package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import java.time.Instant

interface SubmitPhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(itemId: String, photo: ByteArray)
}

class SubmitPhotoSuggestionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val storageProvider: StorageDataProviderProtocol,
) : SubmitPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(itemId: String, photo: ByteArray) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val path = PhotoSuggestionDomain.storagePath(itemId, userId)
        val documentId = PhotoSuggestionDomain.id(itemId, userId)
        val existing: PhotoSuggestionDTO? = dataProvider.loadFromServerAsync(id = documentId, from = Constants.Firestore.PHOTO_SUGGESTIONS)
        if (existing != null) return
        // The rules deny overwriting, and a previous attempt may have uploaded the file and then failed
        // on the Firestore write — so the fixed path is cleared first.
        try {
            storageProvider.deleteAtPathAsync(path)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.STORAGE)
        }
        val photoUrl = storageProvider.uploadAsync(photo, path, Constants.Storage.PHOTO_CONTENT_TYPE)
        val dto = PhotoSuggestionDTO(itemId = itemId, suggestedBy = userId, photoUrl = photoUrl, suggestedAt = Instant.now())
        try {
            dataProvider.setAsync(dto, id = documentId, inCollection = Constants.Firestore.PHOTO_SUGGESTIONS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val concurrent: PhotoSuggestionDTO? = try {
                dataProvider.loadFromServerAsync(id = documentId, from = Constants.Firestore.PHOTO_SUGGESTIONS)
            } catch (reReadError: CancellationException) {
                throw reReadError
            } catch (_: Exception) {
                null
            }
            if (concurrent == null) deleteUploadedFileQuietly(path)
            throw error
        }
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
