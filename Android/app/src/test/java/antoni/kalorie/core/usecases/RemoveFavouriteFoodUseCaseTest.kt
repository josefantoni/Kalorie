package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RemoveFavouriteFoodUseCaseTest {

    // MARK: - Tests

    @Test
    fun removeFavouriteFood_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun removeFavouriteFood_deletesFromUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("12345")

        assertEquals("12345", dataProvider.deletedId)
        assertEquals("users/user-123/favouriteFoods", dataProvider.deletedFromCollection)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<RemoveFavouriteFoodUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return RemoveFavouriteFoodUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
