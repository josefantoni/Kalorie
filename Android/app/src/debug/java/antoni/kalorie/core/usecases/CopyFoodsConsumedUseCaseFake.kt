package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import java.time.Instant

data class CopyFoodsConsumedUseCaseFake(
    val shouldThrow: Boolean = false,
) : CopyFoodsConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(foods: List<FoodConsumedDomain>, toDay: Instant, mealTypeId: String, mealTypes: List<MealTypeDomain>) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
