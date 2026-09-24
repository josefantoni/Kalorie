package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain
import java.time.Instant

data class FetchFoodsConsumedForMonthUseCaseFake(
    val stubbedFoods: List<FoodConsumedDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchFoodsConsumedForMonthUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(month: Instant): List<FoodConsumedDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedFoods
    }
}
