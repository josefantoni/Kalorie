package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchPendingSubmissionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchPendingSubmissions_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchPendingSubmissions_queriesPendingStatusOrderedBySubmittedAtDescending() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.queriedCollection)
        assertEquals("status", dataProvider.queriedField)
        assertEquals(FoodItemSubmissionStatus.PENDING.wireValue, dataProvider.queriedValue)
        assertEquals("submitted_at", dataProvider.queriedOrderByField)
        assertEquals(true, dataProvider.queriedDescending)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Pair<FetchPendingSubmissionsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchPendingSubmissionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
