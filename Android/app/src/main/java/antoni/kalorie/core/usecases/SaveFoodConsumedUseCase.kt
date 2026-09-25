package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.scaled
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.epochSecondsAsDouble
import java.time.Instant
import java.util.UUID

interface SaveFoodConsumedUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain, grams: Double, date: Instant, mealTypeId: String?)
}

class SaveFoodConsumedUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SaveFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, grams: Double, date: Instant, mealTypeId: String?) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val scaled = item.scaled(toGrams = grams)
        val dto = FoodConsumedDTO(
            id = UUID.randomUUID().toString().uppercase(),
            foodItemId = item.id,
            foodItemKind = item.kind,
            czName = item.czName,
            engName = item.engName,
            weight = grams,
            date = date.epochSecondsAsDouble(),
            calories = scaled.calories,
            caloriesPerHundredGrams = item.caloriesPerHundredGrams,
            energyKJ = scaled.energyKJ,
            protein = scaled.protein,
            carbohydrate = scaled.carbohydrate,
            carbohydrateSugar = scaled.carbohydrateSugar,
            fat = scaled.fat,
            fatSaturated = scaled.fatSaturated,
            fatUnsaturated = scaled.fatUnsaturated,
            fiber = scaled.fiber,
            salt = scaled.salt,
            mealTypeId = mealTypeId,
            measureUnit = item.measure.rawValue,
        )
        dataProvider.setAsync(dto, id = dto.id, inCollection = Constants.Firestore.foodConsumed(userId))
    }
}
