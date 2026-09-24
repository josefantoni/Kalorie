package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadArrayContainsAsync
import antoni.kalorie.core.networking.loadHasPrefixAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.textkit.searchQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface SearchFoodItemsUseCaseProtocol {
    suspend operator fun invoke(query: String): List<FoodItemDomain>
}

class SearchFoodItemsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : SearchFoodItemsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(query: String): List<FoodItemDomain> = coroutineScope {
        val searchQuery = searchQuery(query)
        val lowercasedQuery = searchQuery.lowercased
        val foldedQuery = searchQuery.folded
        val foldedLastWord = searchQuery.lastWord
        val byName = async { loadByPrefix("cz_name_lowercase", lowercasedQuery) }
        val byOriginalName = async { loadByPrefix("eng_name_lowercase", lowercasedQuery) }
        val byFoldedName = async { loadByPrefix("cz_name_folded", foldedQuery) }
        val byFoldedOriginalName = async { loadByPrefix("eng_name_folded", foldedQuery) }
        val byCzNameToken = async { loadByToken("cz_name_search_terms", foldedLastWord) }
        val byEngNameToken = async { loadByToken("eng_name_search_terms", foldedLastWord) }
        val seen = mutableSetOf<String>()
        listOf(byName, byOriginalName, byFoldedName, byFoldedOriginalName, byCzNameToken, byEngNameToken)
            .flatMap { it.await() }
            .filter { seen.add(it.id) }
            .map { it.asDomain() }
    }

    // MARK: - Private

    private suspend fun loadByPrefix(field: String, prefix: String): List<FoodItemDTO> =
        dataProvider.loadHasPrefixAsync(from = Constants.Firestore.FOOD_ITEMS, field = field, hasPrefix = prefix, limit = RESULT_LIMIT)

    private suspend fun loadByToken(field: String, token: String): List<FoodItemDTO> =
        dataProvider.loadArrayContainsAsync(from = Constants.Firestore.FOOD_ITEMS, field = field, arrayContains = token, limit = RESULT_LIMIT)

    private companion object {
        const val RESULT_LIMIT = 10
    }
}
