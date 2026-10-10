package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteMySubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteMySubmission_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider, _) = makeSUT(userId = null)

        try {
            sut("sub-1", null)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertNull(dataProvider.deletedId)
    }

    @Test
    fun deleteMySubmission_deletesFromFoodItemSubmissionsCollection() = runTest {
        val (sut, dataProvider, _) = makeSUT(userId = "user-123")

        sut("sub-1", null)

        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.deletedFromCollection)
        assertEquals("sub-1", dataProvider.deletedId)
    }

    @Test
    fun deleteMySubmission_alsoDeletesItsPhoto() = runTest {
        val (sut, _, storage) = makeSUT(userId = "user-123")
        val photo = storage.uploadAsync(byteArrayOf(1), "submissionPhotos/user-123/a.jpg", "image/jpeg")

        sut("sub-1", photo)

        assertEquals(listOf(photo), storage.deletedUrls)
    }

    @Test
    fun deleteMySubmission_whenThePhotoCannotBeDeleted_stillSucceeds() = runTest {
        val (sut, dataProvider, storage) = makeSUT(userId = "user-123")
        storage.deleteError = RuntimeException("offline")

        sut("sub-1", "https://storage.fake/a.jpg")

        assertEquals("the submission is withdrawn; an orphan file is only logged and removed with the account", "sub-1", dataProvider.deletedId)
    }

    @Test
    fun deleteMySubmission_whenTheDocumentCannotBeDeleted_keepsThePhoto() = runTest {
        val (sut, dataProvider, storage) = makeSUT(userId = "user-123")
        dataProvider.stubbedDeleteError = RuntimeException("offline")
        val photo = storage.uploadAsync(byteArrayOf(1), "submissionPhotos/user-123/a.jpg", "image/jpeg")

        try {
            sut("sub-1", photo)
        } catch (_: RuntimeException) {
        }

        assertTrue("a submission that is still there must keep its photo", storage.deletedUrls.isEmpty())
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Triple<DeleteMySubmissionUseCase, FirestoreDataProviderFake, StorageDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = DeleteMySubmissionUseCase(dataProvider, AuthProviderFake(userId = userId), DeleteFoodPhotoUseCase(storage))
        return Triple(sut, dataProvider, storage)
    }
}
