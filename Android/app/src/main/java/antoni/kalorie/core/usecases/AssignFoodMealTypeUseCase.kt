package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants

interface AssignFoodMealTypeUseCaseProtocol {
    suspend operator fun invoke(food: FoodConsumedDomain, mealTypeId: String)
}

class AssignFoodMealTypeUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : AssignFoodMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(food: FoodConsumedDomain, mealTypeId: String) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto = FoodConsumedDTO(food = food, mealTypeId = mealTypeId)
        dataProvider.setAsync(dto, id = food.id, inCollection = Constants.Firestore.foodConsumed(userId))
    }
}
