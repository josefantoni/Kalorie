package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DeleteFoodConsumedUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteFoodConsumed_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(id = "food-1")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun deleteFoodConsumed_deletesFromUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(id = "food-1")

        assertEquals("users/user-123/foodConsumed", dataProvider.deletedFromCollection)
        assertEquals("food-1", dataProvider.deletedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<DeleteFoodConsumedUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = DeleteFoodConsumedUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }
}
