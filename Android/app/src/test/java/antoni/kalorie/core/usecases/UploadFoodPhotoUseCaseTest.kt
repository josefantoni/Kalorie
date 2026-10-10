package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class UploadFoodPhotoUseCaseTest {

    // MARK: - Tests

    @Test
    fun upload_storesTheBytesInTheGivenFolderAsJpeg() = runTest {
        val (sut, storage) = makeSUT()

        val url = sut(byteArrayOf(1, 2, 3), "submissionPhotos/alice")

        val path = storage.uploadedPaths.first()
        assertTrue(path.startsWith("submissionPhotos/alice/"))
        assertTrue(path.endsWith(".jpg"))
        assertArrayEquals(byteArrayOf(1, 2, 3), storage.files[url])
    }

    @Test
    fun upload_neverReusesAFileName() = runTest {
        val (sut, storage) = makeSUT()

        sut(byteArrayOf(1), "submissionPhotos/alice")
        sut(byteArrayOf(2), "submissionPhotos/alice")

        assertEquals("storage rules forbid overwriting, so every upload needs a fresh name", 2, storage.uploadedPaths.toSet().size)
    }

    @Test
    fun upload_whenStorageFails_throws() = runTest {
        val (sut, storage) = makeSUT()
        storage.uploadError = IOException("offline")

        try {
            sut(byteArrayOf(1), "submissionPhotos/alice")
            fail("Expected the storage error")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<UploadFoodPhotoUseCase, StorageDataProviderFake> {
        val storage = StorageDataProviderFake()
        return UploadFoodPhotoUseCase(storage) to storage
    }
}
