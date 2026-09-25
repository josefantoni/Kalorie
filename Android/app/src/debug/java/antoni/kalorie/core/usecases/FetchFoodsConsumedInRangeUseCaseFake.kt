package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain
import java.time.Instant

data class FetchFoodsConsumedInRangeUseCaseFake(
    val stubbedFoods: List<FoodConsumedDomain> = emptyList(),
    val stubbedError: Exception? = null,
) : FetchFoodsConsumedInRangeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(from: Instant, to: Instant): List<FoodConsumedDomain> {
        stubbedError?.let { throw it }
        return stubbedFoods
    }
}
