package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed class ApprovePhotoSuggestionError : Exception() {
    data object ItemMissing : ApprovePhotoSuggestionError()
    data object ItemAlreadyHasPhoto : ApprovePhotoSuggestionError()
}

interface ApprovePhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(suggestion: PhotoSuggestionDomain)
}

class ApprovePhotoSuggestionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    storageProvider: StorageDataProviderProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val downloadFoodPhoto: DownloadFoodPhotoUseCaseProtocol,
    private val updateFoodItem: UpdateFoodItemUseCaseProtocol,
) : ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private val remover = PhotoSuggestionRemover(dataProvider, storageProvider)

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val item = fetchFoodItemByBarcode(suggestion.itemId) ?: throw ApprovePhotoSuggestionError.ItemMissing
        if (item.photoUrl != null) throw ApprovePhotoSuggestionError.ItemAlreadyHasPhoto
        // The suggestion folder is private to its author, so the catalogue gets its own copy under
        // catalogPhotos (UpdateFoodItemUseCase uploads it) rather than a link into photoSuggestions.
        val photoData = downloadFoodPhoto(suggestion.photoUrl)
        withContext(NonCancellable) {
            updateFoodItem(item, item, FoodItemFormPhoto.Local(photoData), validate = false)
            removeAllSuggestions(suggestion.itemId)
        }
    }

    // MARK: - Private

    // Other users' suggestions for the same item would otherwise wait forever: the item has a photo now.
    private suspend fun removeAllSuggestions(itemId: String) {
        try {
            val documentIds = dataProvider.loadDocumentIdsAsync(
                from = Constants.Firestore.PHOTO_SUGGESTIONS,
                field = Constants.Firestore.Field.ITEM_ID,
                isEqualTo = itemId,
            )
            for (documentId in documentIds) {
                val (suggestionItemId, userId) = PhotoSuggestionDomain.ids(documentId) ?: continue
                try {
                    remover.remove(suggestionItemId, userId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.error(error, Constants.LogCategory.MODERATION)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
        }
    }
}
