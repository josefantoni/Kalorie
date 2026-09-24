package antoni.kalorie.core.networking

import kotlinx.serialization.KSerializer

@Suppress("UNCHECKED_CAST")
class FirestoreDataProviderFake : FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDocuments: List<Any> = emptyList()
    var stubbedServerDocuments: List<Any> = emptyList()
    var stubbedRangeDocuments: (Double, Double) -> List<Any> = { _, _ -> emptyList() }
    var stubbedError: Exception? = null
    var batchSavedCollection: String? = null
    var batchSavedCount = 0
    var batchSavedItems: List<Pair<Any?, String>> = emptyList()
    var setSavedCollection: String? = null
    var setSavedId: String? = null
    var setSavedItem: Any? = null
    var deletedFromCollection: String? = null
    var deletedId: String? = null

    // MARK: - Functions

    override suspend fun <T> loadAsync(from: String, serializer: KSerializer<T>): List<T> {
        stubbedError?.let { throw it }
        return stubbedDocuments as List<T>
    }

    override suspend fun <T> loadFromServerAsync(from: String, serializer: KSerializer<T>): List<T> {
        stubbedError?.let { throw it }
        return stubbedServerDocuments as List<T>
    }

    override suspend fun <T> loadAsync(
        from: String,
        field: String,
        isGreaterThanOrEqualTo: Double,
        isLessThan: Double,
        serializer: KSerializer<T>,
    ): List<T> = stubbedRangeDocuments(isGreaterThanOrEqualTo, isLessThan) as List<T>

    override suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        setSavedItem = item
        setSavedId = id
        setSavedCollection = inCollection
    }

    override suspend fun <T> batchSetAsync(items: List<Pair<T, String>>, inCollection: String, serializer: KSerializer<T>) {
        batchSavedItems = items
        batchSavedCollection = inCollection
        batchSavedCount = items.size
    }

    override suspend fun deleteAsync(id: String, from: String) {
        deletedId = id
        deletedFromCollection = from
    }
}
