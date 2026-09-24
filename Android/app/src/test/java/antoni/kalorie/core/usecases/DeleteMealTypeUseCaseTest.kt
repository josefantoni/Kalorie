package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DeleteMealTypeUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteMealType_callsDeleteWithCorrectId() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")
        val mealType = MealTypeDomain(id = "42", name = "Oběd", startMinutes = 0, endMinutes = 0)

        sut(mealType)

        assertEquals("42", dataProvider.deletedId)
        assertEquals("users/user-123/mealTypes", dataProvider.deletedFromCollection)
    }

    @Test
    fun deleteMealType_withoutAuth_throws() = runTest {
        val (sut, _) = makeSUT(userId = null)
        val mealType = MealTypeDomain(id = "1", name = "Test", startMinutes = 0, endMinutes = 0)

        try {
            sut(mealType)
            fail("Expected AuthError.notAuthenticated")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<DeleteMealTypeUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = DeleteMealTypeUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }
}
