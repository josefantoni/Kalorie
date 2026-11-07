package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface FetchHasPendingNotificationsUseCaseProtocol {
    suspend operator fun invoke(): Boolean
}

class FetchHasPendingNotificationsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean = try {
        coroutineScope {
            val counts = listOf(
                async {
                    dataProvider.countAsync(
                        from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
                        field = Constants.Firestore.Field.STATUS,
                        isEqualTo = FoodItemSubmissionStatus.PENDING.wireValue,
                    )
                },
                async { dataProvider.countAsync(from = Constants.Firestore.FOOD_ITEM_REPORTS) },
                async { dataProvider.countAsync(from = Constants.Firestore.PHOTO_SUGGESTIONS) },
            ).awaitAll()
            counts.any { it > 0 }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.warning(error, Constants.LogCategory.MODERATION)
        false
    }
}
