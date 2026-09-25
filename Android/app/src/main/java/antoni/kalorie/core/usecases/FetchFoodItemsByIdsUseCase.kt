package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchFoodItemsByIdsUseCaseProtocol {
    suspend operator fun invoke(ids: List<String>): List<FoodItemDomain>
}

class FetchFoodItemsByIdsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : FetchFoodItemsByIdsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(ids: List<String>): List<FoodItemDomain> {
        val dtos: List<FoodItemDTO> = dataProvider.loadAsync(from = Constants.Firestore.FOOD_ITEMS, whereDocumentIdIn = ids.distinct())
        return dtos.map { it.asDomain() }
    }
}
