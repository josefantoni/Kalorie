package antoni.kalorie.core.networking

import java.io.FileNotFoundException

class StorageDataProviderFake : StorageDataProviderProtocol {

    // MARK: - Properties

    val files: MutableMap<String, ByteArray> = mutableMapOf()
    var uploadError: Exception? = null
    var downloadError: Exception? = null
    var deleteError: Exception? = null
    var listError: Exception? = null
    val uploadedPaths: MutableList<String> = mutableListOf()
    val deletedUrls: MutableList<String> = mutableListOf()

    // MARK: - Functions

    override suspend fun uploadAsync(data: ByteArray, path: String, contentType: String): String {
        uploadError?.let { throw it }
        val url = "https://storage.fake/${path.replace("/", "%2F")}"
        files[url] = data
        uploadedPaths.add(path)
        return url
    }

    override suspend fun downloadAsync(url: String): ByteArray {
        downloadError?.let { throw it }
        return files[url] ?: throw FileNotFoundException(url)
    }

    override suspend fun deleteAsync(url: String) {
        deleteError?.let { throw it }
        files.remove(url)
        deletedUrls.add(url)
    }

    override suspend fun listAsync(prefix: String): List<String> {
        listError?.let { throw it }
        val encodedPrefix = prefix.replace("/", "%2F")
        return files.keys.filter { it.startsWith("https://storage.fake/$encodedPrefix") }.sorted()
    }
}
