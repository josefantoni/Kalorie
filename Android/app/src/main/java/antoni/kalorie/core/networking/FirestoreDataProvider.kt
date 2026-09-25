package antoni.kalorie.core.networking

import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

sealed class FirestoreDataProviderError : Exception() {
    data object Unreachable : FirestoreDataProviderError()
}

interface FirestoreDataProviderProtocol {
    suspend fun <T> loadAsync(from: String, serializer: KSerializer<T>): List<T>
    suspend fun <T> loadAsync(id: String, from: String, serializer: KSerializer<T>): T?
    suspend fun <T> loadAsync(from: String, whereDocumentIdIn: List<String>, serializer: KSerializer<T>): List<T>
    suspend fun <T> loadAsync(
        from: String,
        field: String,
        isEqualTo: String,
        orderBy: String,
        descending: Boolean,
        serializer: KSerializer<T>,
    ): List<T>
    suspend fun <T> loadFromServerAsync(id: String, from: String, serializer: KSerializer<T>): T?
    suspend fun <T> loadFromServerAsync(from: String, serializer: KSerializer<T>): List<T>
    suspend fun <T> loadAsync(from: String, orderBy: String, descending: Boolean, limit: Int, serializer: KSerializer<T>): List<T>
    suspend fun <T> loadAsync(
        from: String,
        field: String,
        isGreaterThanOrEqualTo: Double,
        isLessThan: Double,
        serializer: KSerializer<T>,
    ): List<T>
    suspend fun <T> loadHasPrefixAsync(from: String, field: String, hasPrefix: String, limit: Int, serializer: KSerializer<T>): List<T>
    suspend fun <T> loadArrayContainsAsync(from: String, field: String, arrayContains: String, limit: Int, serializer: KSerializer<T>): List<T>
    suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>)
    suspend fun <T> batchSetAsync(items: List<Pair<T, String>>, inCollection: String, serializer: KSerializer<T>)
    suspend fun deleteAsync(id: String, from: String)
}

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(from: String): List<T> =
    loadAsync(from, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(id: String, from: String): T? =
    loadAsync(id, from, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(
    from: String,
    orderBy: String,
    descending: Boolean,
    limit: Int,
): List<T> = loadAsync(from, orderBy, descending, limit, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(
    from: String,
    whereDocumentIdIn: List<String>,
): List<T> = loadAsync(from, whereDocumentIdIn, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(
    from: String,
    field: String,
    isEqualTo: String,
    orderBy: String,
    descending: Boolean,
): List<T> = loadAsync(from, field, isEqualTo, orderBy, descending, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadFromServerAsync(id: String, from: String): T? =
    loadFromServerAsync(id, from, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadFromServerAsync(from: String): List<T> =
    loadFromServerAsync(from, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadAsync(
    from: String,
    field: String,
    isGreaterThanOrEqualTo: Double,
    isLessThan: Double,
): List<T> = loadAsync(from, field, isGreaterThanOrEqualTo, isLessThan, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadHasPrefixAsync(
    from: String,
    field: String,
    hasPrefix: String,
    limit: Int,
): List<T> = loadHasPrefixAsync(from, field, hasPrefix, limit, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.loadArrayContainsAsync(
    from: String,
    field: String,
    arrayContains: String,
    limit: Int,
): List<T> = loadArrayContainsAsync(from, field, arrayContains, limit, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.setAsync(
    item: T,
    id: String,
    inCollection: String,
): Unit = setAsync(item, id, inCollection, serializer<T>())

suspend inline fun <reified T> FirestoreDataProviderProtocol.batchSetAsync(
    items: List<Pair<T, String>>,
    inCollection: String,
): Unit = batchSetAsync(items, inCollection, serializer<T>())

class FirestoreDataProvider(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : FirestoreDataProviderProtocol {

    // MARK: - Functions

    override suspend fun <T> loadAsync(from: String, serializer: KSerializer<T>): List<T> =
        perform {
            val snapshot = firestore.collection(from).get().await()
            snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
        }

    override suspend fun <T> loadAsync(id: String, from: String, serializer: KSerializer<T>): T? =
        perform {
            val snapshot = firestore.collection(from).document(id).get().await()
            snapshot.data?.let { FirestoreDataMapper.decode(it, serializer) }
        }

    override suspend fun <T> loadAsync(from: String, whereDocumentIdIn: List<String>, serializer: KSerializer<T>): List<T> =
        perform {
            whereDocumentIdIn.chunked(Constants.Firestore.IN_QUERY_LIMIT).flatMap { chunk ->
                val snapshot = firestore.collection(from).whereIn(FieldPath.documentId(), chunk).get().await()
                snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
            }
        }

    override suspend fun <T> loadAsync(
        from: String,
        field: String,
        isEqualTo: String,
        orderBy: String,
        descending: Boolean,
        serializer: KSerializer<T>,
    ): List<T> = perform {
        val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        val snapshot = firestore.collection(from).whereEqualTo(field, isEqualTo).orderBy(orderBy, direction).get().await()
        snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
    }

    override suspend fun <T> loadFromServerAsync(id: String, from: String, serializer: KSerializer<T>): T? =
        perform {
            val snapshot = firestore.collection(from).document(id).get(Source.SERVER).await()
            snapshot.data?.let { FirestoreDataMapper.decode(it, serializer) }
        }

    override suspend fun <T> loadFromServerAsync(from: String, serializer: KSerializer<T>): List<T> =
        perform {
            val snapshot = firestore.collection(from).get(Source.SERVER).await()
            snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
        }

    override suspend fun <T> loadAsync(from: String, orderBy: String, descending: Boolean, limit: Int, serializer: KSerializer<T>): List<T> =
        perform {
            val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
            val snapshot = firestore.collection(from).orderBy(orderBy, direction).limit(limit.toLong()).get().await()
            snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
        }

    override suspend fun <T> loadAsync(
        from: String,
        field: String,
        isGreaterThanOrEqualTo: Double,
        isLessThan: Double,
        serializer: KSerializer<T>,
    ): List<T> = perform {
        val snapshot = firestore
            .collection(from)
            .whereGreaterThanOrEqualTo(field, isGreaterThanOrEqualTo)
            .whereLessThan(field, isLessThan)
            .get()
            .await()
        snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
    }

    override suspend fun <T> loadHasPrefixAsync(
        from: String,
        field: String,
        hasPrefix: String,
        limit: Int,
        serializer: KSerializer<T>,
    ): List<T> = perform {
        val snapshot = firestore
            .collection(from)
            .whereGreaterThanOrEqualTo(field, hasPrefix)
            .whereLessThan(field, hasPrefix + PREFIX_UPPER_BOUND)
            .limit(limit.toLong())
            .get()
            .await()
        snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
    }

    override suspend fun <T> loadArrayContainsAsync(
        from: String,
        field: String,
        arrayContains: String,
        limit: Int,
        serializer: KSerializer<T>,
    ): List<T> = perform {
        val snapshot = firestore
            .collection(from)
            .whereArrayContains(field, arrayContains)
            .limit(limit.toLong())
            .get()
            .await()
        snapshot.documents.map { FirestoreDataMapper.decode(it.data.orEmpty(), serializer) }
    }

    override suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        perform {
            firestore.collection(inCollection).document(id).set(FirestoreDataMapper.encode(item, serializer)).await()
        }
    }

    override suspend fun <T> batchSetAsync(items: List<Pair<T, String>>, inCollection: String, serializer: KSerializer<T>) {
        perform {
            for (chunk in items.chunked(Constants.Firestore.BATCH_WRITE_LIMIT)) {
                val batch = firestore.batch()
                for ((item, id) in chunk) {
                    batch.set(firestore.collection(inCollection).document(id), FirestoreDataMapper.encode(item, serializer))
                }
                batch.commit().await()
            }
        }
    }

    override suspend fun deleteAsync(id: String, from: String) {
        perform {
            firestore.collection(from).document(id).delete().await()
        }
    }

    // MARK: - Private

    private companion object {
        const val PREFIX_UPPER_BOUND = "\uF8FF"
    }

    private suspend fun <R> perform(block: suspend () -> R): R =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FIRESTORE)
            val isUnavailable = error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE
            throw if (isUnavailable) FirestoreDataProviderError.Unreachable else error
        }
}
