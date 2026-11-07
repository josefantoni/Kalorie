package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.deleteIgnoringMissingAtPath
import antoni.kalorie.core.utils.Constants

class PhotoSuggestionRemover(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val storageProvider: StorageDataProviderProtocol,
) {

    // MARK: - Functions

    // A file that is already gone must not keep the document, and so the queue row, alive forever.
    // Any other failure keeps the document so the removal can be retried instead of orphaning the file.
    suspend fun remove(itemId: String, userId: String) {
        storageProvider.deleteIgnoringMissingAtPath(
            path = PhotoSuggestionDomain.storagePath(itemId, userId),
            category = Constants.LogCategory.MODERATION,
        )
        dataProvider.deleteAsync(
            id = PhotoSuggestionDomain.id(itemId, userId),
            from = Constants.Firestore.PHOTO_SUGGESTIONS,
        )
    }
}
