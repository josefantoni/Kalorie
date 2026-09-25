package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DeleteMySubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteMySubmission_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut("sub-1")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertNull(dataProvider.deletedId)
    }

    @Test
    fun deleteMySubmission_deletesFromFoodItemSubmissionsCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("sub-1")

        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.deletedFromCollection)
        assertEquals("sub-1", dataProvider.deletedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<DeleteMySubmissionUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return DeleteMySubmissionUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
