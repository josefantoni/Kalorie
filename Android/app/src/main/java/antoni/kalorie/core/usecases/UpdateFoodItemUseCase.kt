package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemValidation
import antoni.kalorie.core.models.FoodItemValidationError
import antoni.kalorie.core.models.FoodPortionError
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants

sealed class UpdateFoodItemError : Exception() {
    data object InvalidCode : UpdateFoodItemError()
    data object InvalidName : UpdateFoodItemError()
    data object InvalidCalories : UpdateFoodItemError()
    data object InvalidWeight : UpdateFoodItemError()
    data class InvalidPortion(val error: FoodPortionError) : UpdateFoodItemError()
    data object ChangedSinceLoad : UpdateFoodItemError()

    // MARK: - Functions

    companion object {
        fun from(validationError: FoodItemValidationError): UpdateFoodItemError = validationError.mapped(
            invalidCode = InvalidCode,
            invalidName = InvalidName,
            invalidCalories = InvalidCalories,
            invalidWeight = InvalidWeight,
        ) { InvalidPortion(it) }
    }
}

interface UpdateFoodItemUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain)
}

class UpdateFoodItemUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : UpdateFoodItemUseCaseProtocol {

    // MARK: - Functions

    // setAsync replaces the whole document, and foodItems has no submitted_at-like token, so the
    // full document is re-read and compared client-side to catch a concurrent maintainer edit.
    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        FoodItemValidation.validate(item)?.let { throw UpdateFoodItemError.from(it) }
        val current: FoodItemDTO? = dataProvider.loadAsync(id = item.id, from = Constants.Firestore.FOOD_ITEMS)
        if (current?.asDomain() != previouslyLoaded) throw UpdateFoodItemError.ChangedSinceLoad
        dataProvider.setAsync(FoodItemDTO(item), id = item.id, inCollection = Constants.Firestore.FOOD_ITEMS)
    }
}
