package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed class ApprovePhotoSuggestionError : Exception() {
    data object ItemMissing : ApprovePhotoSuggestionError()
}

interface ApprovePhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(suggestion: PhotoSuggestionDomain, response: String)
}

class ApprovePhotoSuggestionUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    storageProvider: StorageDataProviderProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val downloadFoodPhoto: DownloadFoodPhotoUseCaseProtocol,
    private val updateFoodItem: UpdateFoodItemUseCaseProtocol,
) : ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private val remover = PhotoSuggestionRemover(storageProvider)
    private val writer = TicketResolutionWriter(dataProvider)

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain, response: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val trimmedResponse = TicketResolutionWriter.validated(response)
        val item = fetchFoodItemByBarcode(suggestion.itemId) ?: throw ApprovePhotoSuggestionError.ItemMissing
        // The suggestion folder is private to its author, so the catalogue gets its own copy under
        // catalogPhotos (UpdateFoodItemUseCase uploads it, and deletes the photo it replaces) rather
        // than a link into photoSuggestions.
        val photoData = downloadFoodPhoto(suggestion.photoUrl)
        withContext(NonCancellable) {
            updateFoodItem(item, item, FoodItemFormPhoto.Local(photoData), validate = false)
            writer.write(
                status = TicketStatus.ACCEPTED,
                response = trimmedResponse,
                ticketId = suggestion.id,
                inCollection = Constants.Firestore.PHOTO_SUGGESTIONS,
            )
            remover.deleteFileQuietly(suggestion.photoUrl)
        }
    }
}
