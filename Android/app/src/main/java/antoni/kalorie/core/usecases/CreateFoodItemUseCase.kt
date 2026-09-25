package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemValidation
import antoni.kalorie.core.models.FoodItemValidationError
import antoni.kalorie.core.models.FoodPortionError
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.isFirestorePermissionDenied

sealed class CreateFoodItemError : Exception() {
    data object InvalidCode : CreateFoodItemError()
    data object InvalidName : CreateFoodItemError()
    data object InvalidCalories : CreateFoodItemError()
    data object InvalidWeight : CreateFoodItemError()
    data class InvalidPortion(val error: FoodPortionError) : CreateFoodItemError()
    data object ItemAlreadyExists : CreateFoodItemError()

    // MARK: - Functions

    companion object {
        fun from(validationError: FoodItemValidationError): CreateFoodItemError = validationError.mapped(
            invalidCode = InvalidCode,
            invalidName = InvalidName,
            invalidCalories = InvalidCalories,
            invalidWeight = InvalidWeight,
        ) { InvalidPortion(it) }
    }
}

interface CreateFoodItemUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain): FoodItemDomain
}

class CreateFoodItemUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : CreateFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemDomain {
        FoodItemValidation.validate(item)?.let { throw CreateFoodItemError.from(it) }
        val existing: FoodItemDTO? = dataProvider.loadFromServerAsync(id = item.id, from = Constants.Firestore.FOOD_ITEMS)
        if (existing != null) throw CreateFoodItemError.ItemAlreadyExists
        try {
            dataProvider.setAsync(FoodItemDTO(item), id = item.id, inCollection = Constants.Firestore.FOOD_ITEMS)
        } catch (writeError: Exception) {
            if (!writeError.isFirestorePermissionDenied) throw writeError
            // A rule denial does not say why. A duplicate is the expected cause, but an expired
            // auth session mid-request denies with the same code, so re-read before relabelling.
            val confirmedExisting: FoodItemDTO? = try {
                dataProvider.loadFromServerAsync(id = item.id, from = Constants.Firestore.FOOD_ITEMS)
            } catch (_: Exception) {
                null
            }
            if (confirmedExisting == null) throw writeError
            throw CreateFoodItemError.ItemAlreadyExists
        }
        return item
    }
}
