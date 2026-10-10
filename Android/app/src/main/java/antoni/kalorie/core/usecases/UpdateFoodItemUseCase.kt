package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemValidation
import antoni.kalorie.core.models.FoodItemValidationError
import antoni.kalorie.core.models.FoodPortionError
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.CancellationException

sealed class UpdateFoodItemError : Exception() {
    data object InvalidCode : UpdateFoodItemError()
    data object InvalidName : UpdateFoodItemError()
    data object InvalidCalories : UpdateFoodItemError()
    data object InvalidAlcoholByVolume : UpdateFoodItemError()
    data class InvalidPortion(val error: FoodPortionError) : UpdateFoodItemError()
    data object ChangedSinceLoad : UpdateFoodItemError()

    // MARK: - Functions

    companion object {
        fun from(validationError: FoodItemValidationError): UpdateFoodItemError = validationError.mapped(
            invalidCode = InvalidCode,
            invalidName = InvalidName,
            invalidCalories = InvalidCalories,
            invalidAlcoholByVolume = InvalidAlcoholByVolume,
        ) { InvalidPortion(it) }
    }
}

interface UpdateFoodItemUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto)
}

class UpdateFoodItemUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val uploadFoodPhoto: UploadFoodPhotoUseCaseProtocol,
    private val deleteFoodPhoto: DeleteFoodPhotoUseCaseProtocol,
) : UpdateFoodItemUseCaseProtocol {

    // MARK: - Functions

    // setAsync replaces the whole document, and foodItems has no submitted_at-like token, so the
    // full document is re-read and compared client-side to catch a concurrent maintainer edit.
    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        FoodItemValidation.validate(item)?.let { throw UpdateFoodItemError.from(it) }
        val current: FoodItemDTO? = dataProvider.loadAsync(id = item.id, from = Constants.Firestore.FOOD_ITEMS)
        if (current?.asDomain() != previouslyLoaded) throw UpdateFoodItemError.ChangedSinceLoad
        val uploadedUrl = (photo as? FoodItemFormPhoto.Local)?.let {
            uploadFoodPhoto(it.data, "${Constants.Storage.CATALOG_PHOTOS_FOLDER}/${item.id}")
        }
        val dto = FoodItemDTO(item.withPhotoUrl(uploadedUrl ?: item.photoUrl))
        try {
            dataProvider.setAsync(dto, id = item.id, inCollection = Constants.Firestore.FOOD_ITEMS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (uploadedUrl != null) deleteFoodPhoto.deleteQuietly(uploadedUrl)
            throw error
        }
        val previousPhotoUrl = previouslyLoaded.photoUrl
        if (uploadedUrl != null && previousPhotoUrl != null) deleteFoodPhoto.deleteQuietly(previousPhotoUrl)
    }
}
