package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodFrequencyItemDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class RecordFoodFrequencyUseCaseTest {

    // MARK: - Tests

    @Test
    fun recordFoodFrequency_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut(makeItem(id = "12345678"), date = Instant.now())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
            assertNull(dataProvider.incrementedEntryId)
        }
    }

    @Test
    fun recordFoodFrequency_incrementsTheEntryKeyedByFoodIdInTheUserStatsDocument() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeItem(id = "8594000123456"), date = Instant.ofEpochSecond(1_700_000_000))

        assertEquals(
            "the key must be the food id, the same value as food_item_id on foodConsumed",
            "8594000123456",
            dataProvider.incrementedEntryId,
        )
        assertEquals("users/user-123/stats", dataProvider.incrementedCollection)
        assertEquals("foodFrequency", dataProvider.incrementedDocumentId)
        assertEquals(1_700_000_000.0, dataProvider.incrementedLastLoggedAt)
    }

    @Test
    fun recordFoodFrequency_storesTheFullItemSnapshotWithoutFavouriteOnlyFields() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(id = "1", czName = "Tvaroh"), date = Instant.now())

        val snapshot = dataProvider.incrementedItem as FoodFrequencyItemDTO
        assertEquals("1", snapshot.id)
        assertEquals("Tvaroh", snapshot.czName)
        assertEquals(FoodItemKind.CATALOGUE, snapshot.foodItemKind)
        val encoded = FirestoreDataMapper.encode(snapshot, FoodFrequencyItemDTO.serializer())
        assertFalse(encoded.containsKey("favourited_at"))
        assertTrue(encoded.containsKey("food_item_kind"))
    }

    @Test
    fun recordFoodFrequency_forACreatedMeal_incrementsTheEntryKeyedByTheMealId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(id = "meal-1", kind = FoodItemKind.CREATED_MEAL), date = Instant.now())

        assertEquals("a created meal must be counted so it can outrank a once-logged catalogue food", "meal-1", dataProvider.incrementedEntryId)
        assertEquals(FoodItemKind.CREATED_MEAL, (dataProvider.incrementedItem as FoodFrequencyItemDTO).foodItemKind)
    }

    @Test
    fun recordFoodFrequency_whenProviderThrows_propagatesTheError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedIncrementError = IllegalStateException("boom")

        try {
            sut(makeItem(id = "1"), date = Instant.now())
            fail("Expected the provider error")
        } catch (error: IllegalStateException) {
            assertEquals("boom", error.message)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<RecordFoodFrequencyUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return RecordFoodFrequencyUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeItem(id: String, czName: String = "Tvaroh", kind: FoodItemKind = FoodItemKind.CATALOGUE): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = kind,
        czName = czName,
        engName = "",
        weight = 100.0,
        date = Instant.now(),
        energyKJ = 0.0,
        caloriesPerHundredGrams = 100.0,
        fat = 0.0,
        fatSaturated = 0.0,
        fatUnsaturatedFattyAcids = 0.0,
        carbohydrate = 0.0,
        carbohydratePureSugar = 0.0,
        fiber = 0.0,
        protein = 0.0,
        salt = 0.0,
    )
}
