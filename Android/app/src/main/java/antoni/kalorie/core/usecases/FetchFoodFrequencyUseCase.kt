package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodFrequencyEntry
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodFrequencyDocumentDTO
import antoni.kalorie.core.networking.FoodFrequencyEntryDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.instantFromEpochSeconds
import kotlinx.coroutines.CancellationException

interface FetchFoodFrequencyUseCaseProtocol {
    suspend operator fun invoke(): Map<String, FoodFrequencyEntry>
}

class FetchFoodFrequencyUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchFoodFrequencyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Map<String, FoodFrequencyEntry> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val collection = Constants.Firestore.stats(userId)
        val document: FoodFrequencyDocumentDTO = dataProvider.loadAsync(id = Constants.Firestore.FOOD_FREQUENCY_DOCUMENT_ID, from = collection)
            ?: return emptyMap()
        val ranked = document.decodedEntries().entries.sortedWith(
            compareByDescending<Map.Entry<String, FoodFrequencyEntryDTO>> { it.value.lastLoggedAt }
                .thenByDescending { it.value.count }
                .thenBy { it.key },
        )
        val kept = ranked.take(Constants.Search.FREQUENCY_ENTRY_LIMIT)
        val excessIds = ranked.drop(Constants.Search.FREQUENCY_ENTRY_LIMIT).map { it.key } + document.undecodableIds()
        if (excessIds.isNotEmpty()) {
            try {
                dataProvider.deleteEntriesAsync(
                    ids = excessIds,
                    documentId = Constants.Firestore.FOOD_FREQUENCY_DOCUMENT_ID,
                    inCollection = collection,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            }
        }
        return kept.associate { (id, entry) ->
            id to FoodFrequencyEntry(
                count = entry.count,
                lastLoggedAt = instantFromEpochSeconds(entry.lastLoggedAt),
                item = entry.item.asDomain(),
            )
        }
    }
}
