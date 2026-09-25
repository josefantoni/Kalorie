package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

object FoodItemSubmissionFetcher {

    // MARK: - Functions

    suspend fun fetch(field: String, isEqualTo: String, dataProvider: FirestoreDataProviderProtocol): List<FoodItemSubmissionDomain> {
        val dtos: List<FoodItemSubmissionDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            field = field,
            isEqualTo = isEqualTo,
            orderBy = "submitted_at",
            descending = true,
        )
        return dtos.map { it.asDomain() }
    }
}
