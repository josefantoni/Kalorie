package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupDefaultMealsUseCaseTest {

    // MARK: - Tests

    @Test
    fun setupDefaultMeals_returnsExactlyFiveMealTypes() = runTest {
        val (sut, _) = makeSUT()

        val result = sut()

        assertEquals(5, result.size)
    }

    @Test
    fun setupDefaultMeals_persistsFiveMealTypesToDataProvider() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        assertEquals(5, dataProvider.batchSavedCount)
        assertEquals("users/test-user-id/mealTypes", dataProvider.batchSavedCollection)
    }

    @Test
    fun setupDefaultMeals_assignsDistinctNonEmptyIds() = runTest {
        val (sut, _) = makeSUT()

        val result = sut()

        val ids = result.map { it.id }.toSet()
        assertEquals("each default meal must get its own id, or setAsync would silently overwrite one with another", 5, ids.size)
        assertTrue(ids.all { it.isNotEmpty() })
    }

    @Test
    fun setupDefaultMeals_writesUppercaseIds() = runTest {
        val (sut, _) = makeSUT()

        val result = sut()

        assertTrue("the rules refuse a lowercase UUID without saying why", result.all { it.id == it.id.uppercase() })
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<SetupDefaultMealsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = SetupDefaultMealsUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(),
            mealNames = listOf("Snídaně", "Druhá snídaně", "Oběd", "Svačina", "Večeře"),
        )
        return sut to dataProvider
    }
}
