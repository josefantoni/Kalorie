package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodFrequencyItemDTO
import antoni.kalorie.core.networking.incrementEntryAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.epochSecondsAsDouble
import java.time.Instant

interface RecordFoodFrequencyUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain, date: Instant)
}

class RecordFoodFrequencyUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : RecordFoodFrequencyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, date: Instant) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.incrementEntryAsync(
            item = FoodFrequencyItemDTO(item),
            entryId = item.id,
            lastLoggedAt = date.epochSecondsAsDouble(),
            documentId = Constants.Firestore.FOOD_FREQUENCY_DOCUMENT_ID,
            inCollection = Constants.Firestore.stats(userId),
        )
    }
}
