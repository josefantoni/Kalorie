package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DeleteMyCreatedMealUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteMyCreatedMeal_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("meal-1")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun deleteMyCreatedMeal_deletesFromUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("meal-1")

        assertEquals("users/user-123/myCreatedMeals", dataProvider.deletedFromCollection)
        assertEquals("meal-1", dataProvider.deletedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<DeleteMyCreatedMealUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return DeleteMyCreatedMealUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
