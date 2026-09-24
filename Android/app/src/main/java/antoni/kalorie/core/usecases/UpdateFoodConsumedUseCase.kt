package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.ScaledMacros
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.epochSecondsAsDouble

sealed class UpdateFoodConsumedError : Exception() {
    data object InvalidWeight : UpdateFoodConsumedError()
}

interface UpdateFoodConsumedUseCaseProtocol {
    suspend operator fun invoke(food: FoodConsumedDomain, newWeight: Double)
}

class UpdateFoodConsumedUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : UpdateFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(food: FoodConsumedDomain, newWeight: Double) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        if (food.weight <= 0) throw UpdateFoodConsumedError.InvalidWeight
        val scaled = ScaledMacros(food = food, newWeight = newWeight)
        val dto = FoodConsumedDTO(
            id = food.id,
            foodItemId = food.foodItemId,
            foodItemKind = food.foodItemKind,
            czName = food.czName,
            engName = food.engName,
            weight = newWeight,
            date = food.date.epochSecondsAsDouble(),
            calories = scaled.calories,
            caloriesPerHundredGrams = food.caloriesPerHundredGrams,
            energyKJ = scaled.energyKJ,
            protein = scaled.protein,
            carbohydrate = scaled.carbohydrate,
            carbohydrateSugar = scaled.carbohydrateSugar,
            fat = scaled.fat,
            fatSaturated = scaled.fatSaturated,
            fatUnsaturated = scaled.fatUnsaturated,
            fiber = scaled.fiber,
            salt = scaled.salt,
            mealTypeId = food.mealTypeId,
            measureUnit = food.measure.rawValue,
        )
        dataProvider.setAsync(dto, id = food.id, inCollection = Constants.Firestore.foodConsumed(userId))
    }
}
