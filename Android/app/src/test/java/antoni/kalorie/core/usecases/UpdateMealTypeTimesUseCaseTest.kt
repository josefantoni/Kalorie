package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MealTypeDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateMealTypeTimesUseCaseTest {

    // MARK: - Tests

    @Test
    fun updateMealTypeTimes_writesToUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(listOf(makeMealType()))

        assertEquals("users/user-123/mealTypes", dataProvider.batchSavedCollection)
    }

    @Test
    fun updateMealTypeTimes_writesDomainMinutesVerbatim() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeMealType(startMinutes = 450, endMinutes = 540)))

        val dto = dataProvider.batchSavedItems.first().first as MealTypeDTO
        assertEquals(450, dto.startMinutes)
        assertEquals(540, dto.endMinutes)
    }

    @Test
    fun updateMealTypeTimes_preservesOrderAndCount() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeMealType(id = "breakfast"), makeMealType(id = "lunch"), makeMealType(id = "dinner")))

        assertEquals(listOf("breakfast", "lunch", "dinner"), dataProvider.batchSavedItems.map { it.second })
    }

    @Test
    fun updateMealTypeTimes_preservesIdAndName() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeMealType(id = "lunch", name = "Oběd")))

        val dto = dataProvider.batchSavedItems.first().first as MealTypeDTO
        assertEquals("lunch", dto.id)
        assertEquals("Oběd", dto.name)
    }

    @Test
    fun updateMealTypeTimes_whenNotAuthenticated_throwsAuthErrorAndNeverWrites() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut(listOf(makeMealType()))
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
        assertTrue(dataProvider.batchSavedItems.isEmpty())
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<UpdateMealTypeTimesUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = UpdateMealTypeTimesUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }

    private fun makeMealType(
        id: String = "lunch",
        name: String = "Oběd",
        startMinutes: Int = 0,
        endMinutes: Int = 0,
    ): MealTypeDomain = MealTypeDomain(id = id, name = name, startMinutes = startMinutes, endMinutes = endMinutes)
}
