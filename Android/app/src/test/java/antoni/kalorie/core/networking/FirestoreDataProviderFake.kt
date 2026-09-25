package antoni.kalorie.core.networking

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.KSerializer

@Suppress("UNCHECKED_CAST")
class FirestoreDataProviderFake : FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDocuments: List<Any> = emptyList()
    var stubbedDocumentsByCollection: Map<String, List<Any>> = emptyMap()
    var batchSavedItemsByCollection: Map<String, List<Pair<Any?, String>>> = emptyMap()
    var deletedIdsByCollection: Map<String, List<String>> = emptyMap()
    var deletionCollectionOrder: List<String> = emptyList()
    var stubbedServerDocuments: List<Any> = emptyList()
    var stubbedRangeDocuments: (Double, Double) -> List<Any> = { _, _ -> emptyList() }
    var stubbedByPrefixField: Map<String, List<Any>> = emptyMap()
    var stubbedByArrayContainsField: Map<String, List<Any>> = emptyMap()
    val arrayContainsValuesByField = ConcurrentHashMap<String, String>()
    var stubbedByDocumentIds: List<Any> = emptyList()
    var queriedDocumentIds: List<String> = emptyList()
    var stubbedDocument: Any? = null
    var stubbedServerDocument: Any? = null
    var stubbedServerDocumentSequence: List<Any?> = emptyList()
    var stubbedServerReadError: Exception? = null
    var serverReadsBeforeError = 0
    private var serverReadAttempts = 0
    var stubbedSetError: Exception? = null
    var stubbedDeleteError: Exception? = null
    private var serverReadCount = 0
    var stubbedByField: List<Any> = emptyList()
    var stubbedByFieldByCollection: Map<String, List<Any>> = emptyMap()
    var queriedField: String? = null
    var queriedValue: String? = null
    var queriedServerId: String? = null
    var queriedServerCollection: String? = null
    var stubbedDocumentByCollection: Map<String, Any?> = emptyMap()
    var loadedIdCollections: List<String> = emptyList()
    var queriedCollection: String? = null
    var queriedOrderByField: String? = null
    var queriedDescending: Boolean? = null
    var queriedLimit: Int? = null
    var lastQueriedId: String? = null
    var lastQueriedCollection: String? = null
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
        return (stubbedDocumentsByCollection[from] ?: stubbedDocuments) as List<T>
    }

    override suspend fun <T> loadAsync(id: String, from: String, serializer: KSerializer<T>): T? {
        stubbedError?.let { throw it }
        lastQueriedId = id
        lastQueriedCollection = from
        loadedIdCollections = loadedIdCollections + from
        return (if (stubbedDocumentByCollection.containsKey(from)) stubbedDocumentByCollection[from] else stubbedDocument) as T?
    }

    override suspend fun <T> loadAsync(from: String, orderBy: String, descending: Boolean, limit: Int, serializer: KSerializer<T>): List<T> {
        stubbedError?.let { throw it }
        queriedCollection = from
        queriedOrderByField = orderBy
        queriedDescending = descending
        queriedLimit = limit
        return stubbedDocuments as List<T>
    }

    override suspend fun <T> loadAsync(from: String, whereDocumentIdIn: List<String>, serializer: KSerializer<T>): List<T> {
        stubbedError?.let { throw it }
        queriedCollection = from
        queriedDocumentIds = whereDocumentIdIn
        return stubbedByDocumentIds as List<T>
    }

    override suspend fun <T> loadAsync(
        from: String,
        field: String,
        isEqualTo: String,
        orderBy: String,
        descending: Boolean,
        serializer: KSerializer<T>,
    ): List<T> {
        stubbedError?.let { throw it }
        queriedCollection = from
        queriedField = field
        queriedValue = isEqualTo
        queriedOrderByField = orderBy
        queriedDescending = descending
        return (stubbedByFieldByCollection[from] ?: stubbedByField) as List<T>
    }

    override suspend fun <T> loadFromServerAsync(id: String, from: String, serializer: KSerializer<T>): T? {
        stubbedError?.let { throw it }
        stubbedServerReadError?.let { if (serverReadAttempts++ >= serverReadsBeforeError) throw it }
        queriedServerId = id
        queriedServerCollection = from
        if (stubbedServerDocumentSequence.isNotEmpty()) {
            return stubbedServerDocumentSequence[minOf(serverReadCount++, stubbedServerDocumentSequence.lastIndex)] as T?
        }
        return stubbedServerDocument as T?
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

    override suspend fun <T> loadHasPrefixAsync(
        from: String,
        field: String,
        hasPrefix: String,
        limit: Int,
        serializer: KSerializer<T>,
    ): List<T> = stubbedByPrefixField[field].orEmpty() as List<T>

    override suspend fun <T> loadArrayContainsAsync(
        from: String,
        field: String,
        arrayContains: String,
        limit: Int,
        serializer: KSerializer<T>,
    ): List<T> {
        arrayContainsValuesByField[field] = arrayContains
        return stubbedByArrayContainsField[field].orEmpty() as List<T>
    }

    override suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        stubbedSetError?.let { throw it }
        setSavedItem = item
        setSavedId = id
        setSavedCollection = inCollection
    }

    override suspend fun <T> batchSetAsync(items: List<Pair<T, String>>, inCollection: String, serializer: KSerializer<T>) {
        batchSavedItems = items
        batchSavedItemsByCollection = batchSavedItemsByCollection + (inCollection to items)
        batchSavedCollection = inCollection
        batchSavedCount = items.size
    }

    override suspend fun deleteAsync(id: String, from: String) {
        stubbedDeleteError?.let { throw it }
        deletedId = id
        deletedFromCollection = from
        deletedIdsByCollection = deletedIdsByCollection + (from to (deletedIdsByCollection[from].orEmpty() + id))
        deletionCollectionOrder = deletionCollectionOrder + from
    }
}
