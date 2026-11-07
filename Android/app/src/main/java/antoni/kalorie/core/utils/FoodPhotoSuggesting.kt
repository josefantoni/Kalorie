package antoni.kalorie.core.utils

import antoni.kalorie.R
import antoni.kalorie.components.FoodPhotoPillState
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.FetchMyPhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.usecases.SubmitPhotoSuggestionUseCaseProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

interface FoodPhotoSuggesting {
    val photoPillState: MutableStateFlow<FoodPhotoPillState>
    val alertItem: MutableStateFlow<AlertItem?>

    // MARK: - Functions

    // The pill is decided from the freshly fetched catalogue item, never from a favourite or frequency
    // snapshot: those keep a stale photo_url after an approval and would ask for a photo that exists.
    suspend fun resolveCataloguePhotoPill(
        fetchedItem: FoodItemDomain?,
        fetchMyPhotoSuggestion: FetchMyPhotoSuggestionUseCaseProtocol,
    ) {
        if (fetchedItem == null) {
            photoPillState.value = FoodPhotoPillState.Hidden
            return
        }
        val url = fetchedItem.photoUrl
        if (url != null) {
            photoPillState.value = FoodPhotoPillState.Photo(url)
            return
        }
        photoPillState.value = try {
            if (fetchMyPhotoSuggestion(fetchedItem.id)) FoodPhotoPillState.AwaitingApproval else FoodPhotoPillState.Empty
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.STORAGE)
            FoodPhotoPillState.Hidden
        }
    }

    suspend fun onPhotoSent(
        photo: ByteArray,
        itemId: String,
        submitPhotoSuggestion: SubmitPhotoSuggestionUseCaseProtocol,
    ) {
        try {
            submitPhotoSuggestion(itemId, photo)
            photoPillState.value = FoodPhotoPillState.AwaitingApproval
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.STORAGE)
            alertItem.value = AlertItem(titleRes = R.string.foodPhoto_suggestion_errorSendFailed)
        }
    }
}
