package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import java.time.Instant

data class RecordFoodFrequencyUseCaseFake(
    val shouldThrow: Boolean = false,
) : RecordFoodFrequencyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, date: Instant) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
