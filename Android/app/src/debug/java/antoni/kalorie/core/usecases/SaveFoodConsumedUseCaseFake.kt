package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import java.time.Instant

data class SaveFoodConsumedUseCaseFake(
    val shouldThrow: Boolean = false,
) : SaveFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, grams: Double, date: Instant, mealTypeId: String?) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
