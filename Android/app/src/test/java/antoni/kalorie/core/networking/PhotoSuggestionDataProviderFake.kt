package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.utils.Constants
import kotlinx.serialization.KSerializer

@Suppress("UNCHECKED_CAST")
class PhotoSuggestionDataProviderFake(
    private val base: FirestoreDataProviderProtocol = FirestoreDataProviderFake(),
) : FirestoreDataProviderProtocol by base {

    // MARK: - Properties

    var currentItem: FoodItemDomain? = null
    var stubbedSuggestionDocumentIds: List<String> = emptyList()
    var stubbedSetError: Exception? = null
    var stubbedLoadError: Exception? = null
    val stubbedExistingSuggestions: MutableMap<String, PhotoSuggestionDTO> = mutableMapOf()
    val suggestionsAppearingAfterFailedSet: MutableMap<String, PhotoSuggestionDTO> = mutableMapOf()
    val setCalls: MutableList<Triple<String, String, Any?>> = mutableListOf()
    val deletedIds: MutableList<Pair<String, String>> = mutableListOf()

    // MARK: - Functions

    override suspend fun <T> loadAsync(id: String, from: String, serializer: KSerializer<T>): T? {
        val item = currentItem
        if (from != Constants.Firestore.FOOD_ITEMS || item == null) return null
        return FoodItemDTO(item) as T
    }

    override suspend fun <T> loadFromServerAsync(id: String, from: String, serializer: KSerializer<T>): T? {
        stubbedLoadError?.let { throw it }
        if (from == Constants.Firestore.PHOTO_SUGGESTIONS) stubbedExistingSuggestions[id]?.let { return it as T }
        return setCalls.lastOrNull { it.first == id && it.second == from }?.third as T?
    }

    override suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        stubbedSetError?.let {
            stubbedExistingSuggestions.putAll(suggestionsAppearingAfterFailedSet)
            throw it
        }
        setCalls.add(Triple(id, inCollection, item))
    }

    override suspend fun deleteAsync(id: String, from: String) {
        deletedIds.add(id to from)
    }

    override suspend fun loadDocumentIdsAsync(from: String, field: String, isEqualTo: String): List<String> = if (from == Constants.Firestore.PHOTO_SUGGESTIONS) stubbedSuggestionDocumentIds else emptyList()
}

fun makePhotoSuggestionItem(id: String = "12345678", photoUrl: String? = null, caloriesPerHundredGrams: Double = 80.0): FoodItemDomain = FoodItemDomain(
    id = id,
    kind = antoni.kalorie.core.models.FoodItemKind.CATALOGUE,
    czName = "Tvaroh",
    engName = "",
    weight = 200.0,
    date = java.time.Instant.ofEpochSecond(1_700_000_000),
    energyKJ = 335.0,
    caloriesPerHundredGrams = caloriesPerHundredGrams,
    fat = 0.5,
    fatSaturated = 0.3,
    fatUnsaturatedFattyAcids = 0.2,
    carbohydrate = 4.0,
    carbohydratePureSugar = 3.0,
    fiber = 0.0,
    protein = 13.0,
    salt = 0.1,
    photoUrl = photoUrl,
)
