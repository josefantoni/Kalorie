package antoni.kalorie.core.networking

import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

interface StorageDataProviderProtocol {
    suspend fun uploadAsync(data: ByteArray, path: String, contentType: String): String
    suspend fun downloadAsync(url: String): ByteArray
    suspend fun deleteAsync(url: String)
    suspend fun listAsync(prefix: String): List<String>
}

class StorageDataProvider(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : StorageDataProviderProtocol {

    // MARK: - Functions

    override suspend fun uploadAsync(data: ByteArray, path: String, contentType: String): String = logged("PUT $path") {
        val reference = storage.getReference(path)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCacheControl(Constants.Storage.PHOTO_CACHE_CONTROL)
            .build()
        reference.putBytes(data, metadata).await()
        reference.downloadUrl.await().toString()
    }

    override suspend fun downloadAsync(url: String): ByteArray = logged("GET $url") {
        storage.getReferenceFromUrl(url).getBytes(Constants.Storage.MAX_DOWNLOAD_BYTES).await()
    }

    override suspend fun deleteAsync(url: String) = logged("DELETE $url") {
        storage.getReferenceFromUrl(url).delete().await()
        Unit
    }

    override suspend fun listAsync(prefix: String): List<String> = logged("LIST $prefix") {
        storage.getReference(prefix).listAll().await().items.map { it.downloadUrl.await().toString() }
    }

    private suspend fun <T> logged(operation: String, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.error(RuntimeException("$operation failed", error), Constants.LogCategory.STORAGE)
        throw error
    }
}
