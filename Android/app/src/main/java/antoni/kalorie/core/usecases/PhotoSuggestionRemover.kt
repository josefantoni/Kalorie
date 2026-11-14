package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.deleteIgnoringMissing
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException

class PhotoSuggestionRemover(
    private val storageProvider: StorageDataProviderProtocol,
) {

    // MARK: - Functions

    suspend fun deleteFile(photoUrl: String) {
        storageProvider.deleteIgnoringMissing(url = photoUrl, category = Constants.LogCategory.MODERATION)
    }

    suspend fun deleteFileQuietly(photoUrl: String) {
        try {
            deleteFile(photoUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
        }
    }
}
