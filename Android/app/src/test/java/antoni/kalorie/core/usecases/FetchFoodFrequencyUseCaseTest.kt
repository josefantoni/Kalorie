package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodFrequencyDocumentDTO
import antoni.kalorie.core.networking.FoodFrequencyItemDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class FetchFoodFrequencyUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchFoodFrequency_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchFoodFrequency_readsTheSingleStatsDocument() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut()

        assertEquals("users/user-123/stats", dataProvider.lastQueriedCollection)
        assertEquals("foodFrequency", dataProvider.lastQueriedId)
    }

    @Test
    fun fetchFoodFrequency_whenDocumentMissing_returnsEmptyMap() = runTest {
        val (sut, _) = makeSUT()

        assertTrue(sut().isEmpty())
    }

    @Test
    fun fetchFoodFrequency_mapsEntriesByFoodId() = runTest {
        val (sut, dataProvider) = makeSUT()
        stub(dataProvider, mapOf("a" to entry(id = "a", count = 3, lastLoggedAt = 100.0)))

        val result = sut()

        assertEquals(3, result["a"]?.count)
        assertEquals(Instant.ofEpochSecond(100), result["a"]?.lastLoggedAt)
        assertEquals("a", result["a"]?.item?.id)
    }

    @Test
    fun fetchFoodFrequency_dropsAnEntryWithoutDecodableItemInsteadOfFailingTheWholeDocument() = runTest {
        val (sut, dataProvider) = makeSUT()
        stub(
            dataProvider,
            mapOf(
                "good" to entry(id = "good", count = 1, lastLoggedAt = 1.0),
                "broken" to mapOf("count" to 5L, "last_logged_at" to 1.0),
            ),
        )

        assertEquals(setOf("good"), sut().keys)
    }

    @Test
    fun fetchFoodFrequency_withAnEntryWithoutDecodableItem_deletesItSoItDoesNotCountTowardTheSizeLimit() = runTest {
        val (sut, dataProvider) = makeSUT()
        stub(
            dataProvider,
            mapOf(
                "good" to entry(id = "good", count = 1, lastLoggedAt = 1.0),
                "broken" to mapOf("count" to 5L, "last_logged_at" to 1.0),
            ),
        )

        sut()

        assertEquals(listOf("broken"), dataProvider.deletedEntryIds)
    }

    @Test
    fun fetchFoodFrequency_withMoreThanTheLimit_trimsTheLeastRecentlyLoggedAndDeletesTheRest() = runTest {
        val (sut, dataProvider) = makeSUT()
        val limit = Constants.Search.FREQUENCY_ENTRY_LIMIT
        val entries = (0 until limit).associate { "id-$it" to entry(id = "id-$it", count = 2, lastLoggedAt = 100.0 + it) } +
            mapOf(
                "old-frequent" to entry(id = "old-frequent", count = 50, lastLoggedAt = 1.0),
                "old-rare" to entry(id = "old-rare", count = 1, lastLoggedAt = 2.0),
            )
        stub(dataProvider, entries)

        val result = sut()

        assertEquals(limit, result.size)
        assertNull(result["old-frequent"])
        assertNull(result["old-rare"])
        assertEquals(setOf("old-frequent", "old-rare"), dataProvider.deletedEntryIds.toSet())
    }

    @Test
    fun fetchFoodFrequency_withAFullTable_keepsAFoodLoggedForTheFirstTime() = runTest {
        val (sut, dataProvider) = makeSUT()
        val limit = Constants.Search.FREQUENCY_ENTRY_LIMIT
        val entries = (0 until limit).associate { "id-$it" to entry(id = "id-$it", count = 5, lastLoggedAt = 100.0 + it) } +
            mapOf("fresh" to entry(id = "fresh", count = 1, lastLoggedAt = 1_000.0))
        stub(dataProvider, entries)

        val result = sut()

        assertEquals("a count of 1 must not make a just-logged food the first to go, or it could never reach 2", 1, result["fresh"]?.count)
        assertNull(result["id-0"])
    }

    @Test
    fun fetchFoodFrequency_whenTrimFails_stillReturnsTheTrimmedMap() = runTest {
        val (sut, dataProvider) = makeSUT()
        val limit = Constants.Search.FREQUENCY_ENTRY_LIMIT
        stub(dataProvider, (0..limit).associate { "id-$it" to entry(id = "id-$it", count = it + 1, lastLoggedAt = 1.0) })
        dataProvider.stubbedDeleteEntriesError = IllegalStateException("boom")

        val result = sut()

        assertEquals(limit, result.size)
        assertNull(result["id-0"])
    }

    @Test
    fun fetchFoodFrequency_withinTheLimit_deletesNothing() = runTest {
        val (sut, dataProvider) = makeSUT()
        stub(dataProvider, mapOf("a" to entry(id = "a", count = 1, lastLoggedAt = 1.0)))

        sut()

        assertTrue(dataProvider.deletedEntryIds.isEmpty())
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchFoodFrequencyUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodFrequencyUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun stub(dataProvider: FirestoreDataProviderFake, entries: Map<String, Map<String, Any?>>) {
        val document = FirestoreDataMapper.decode(mapOf("entries" to entries), FoodFrequencyDocumentDTO.serializer())
        dataProvider.stubbedDocumentByCollection = mapOf(Constants.Firestore.stats("test-user") to document)
    }

    private fun entry(id: String, count: Int, lastLoggedAt: Double): Map<String, Any?> = mapOf(
        "count" to count.toLong(),
        "last_logged_at" to lastLoggedAt,
        "item" to FirestoreDataMapper.encode(FoodFrequencyItemDTO(makeItem(id)), FoodFrequencyItemDTO.serializer()),
    )

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
