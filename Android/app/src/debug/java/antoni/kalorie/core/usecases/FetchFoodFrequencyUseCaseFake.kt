package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodFrequencyEntry

data class FetchFoodFrequencyUseCaseFake(
    val stubbedEntries: Map<String, FoodFrequencyEntry> = emptyMap(),
    val shouldThrow: Boolean = false,
) : FetchFoodFrequencyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Map<String, FoodFrequencyEntry> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedEntries
    }
}
