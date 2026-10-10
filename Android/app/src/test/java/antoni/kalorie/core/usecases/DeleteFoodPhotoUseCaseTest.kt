package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DeleteFoodPhotoUseCaseTest {

    // MARK: - Tests

    @Test
    fun delete_removesTheFileBehindTheUrl() = runTest {
        val (sut, storage) = makeSUT()
        val url = storage.uploadAsync(byteArrayOf(1), "submissionPhotos/alice/a.jpg", "image/jpeg")

        sut(url)

        assertEquals(listOf(url), storage.deletedUrls)
        assertNull(storage.files[url])
    }

    @Test
    fun delete_whenStorageFails_throws() = runTest {
        val (sut, storage) = makeSUT()
        storage.deleteError = IOException("offline")

        try {
            sut("https://storage.fake/a.jpg")
            fail("Expected the storage error")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<DeleteFoodPhotoUseCase, StorageDataProviderFake> {
        val storage = StorageDataProviderFake()
        return DeleteFoodPhotoUseCase(storage) to storage
    }
}
