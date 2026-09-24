package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodsConsumedForMonthUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchFoodsConsumedForMonth_withEmptyProvider_returnsEmptyArray() = runTest {
        val (sut, _) = makeSUT()

        val result = sut(Instant.now())

        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchFoodsConsumedForMonth_withStubbedItemsInMonth_returnsAll() = runTest {
        val (sut, dataProvider) = makeSUT()
        val march = makeDate(2026, 3, 15)
        dataProvider.stub(
            makeDTO(id = "1", czName = "Vejce", date = march),
            makeDTO(id = "2", czName = "Chléb", date = makeDate(2026, 3, 20)),
        )

        val result = sut(march)

        assertEquals(2, result.size)
        assertTrue(result.any { it.czName == "Vejce" })
        assertTrue(result.any { it.czName == "Chléb" })
    }

    @Test
    fun fetchFoodsConsumedForMonth_filtersOutItemsFromPreviousAndNextMonth() = runTest {
        val (sut, dataProvider) = makeSUT()
        val march = makeDate(2026, 3, 15)
        dataProvider.stub(
            makeDTO(id = "1", czName = "Únorové jídlo", date = makeDate(2026, 2, 28)),
            makeDTO(id = "2", czName = "Březnové jídlo", date = march),
            makeDTO(id = "3", czName = "Dubnové jídlo", date = makeDate(2026, 4, 1)),
        )

        val result = sut(march)

        assertEquals(1, result.size)
        assertEquals("Březnové jídlo", result[0].czName)
    }

    @Test
    fun fetchFoodsConsumedForMonth_includesStartOfMonthAndExcludesStartOfNextMonth() = runTest {
        val (sut, dataProvider) = makeSUT()
        val startOfMonth = makeDate(2026, 3, 1)
        val startOfNextMonth = makeDate(2026, 4, 1)
        dataProvider.stub(
            makeDTO(id = "1", czName = "První den měsíce", date = startOfMonth),
            makeDTO(id = "2", czName = "První den dalšího měsíce", date = startOfNextMonth),
        )

        val result = sut(startOfMonth)

        assertEquals(1, result.size)
        assertEquals("První den měsíce", result[0].czName)
    }

    @Test
    fun fetchFoodsConsumedForMonth_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(Instant.now())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchFoodsConsumedForMonthUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchFoodsConsumedForMonthUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }

    private fun FirestoreDataProviderFake.stub(vararg dtos: FoodConsumedDTO) {
        stubbedRangeDocuments = { lowerBound, upperBound -> dtos.filter { it.date >= lowerBound && it.date < upperBound } }
    }

    private fun makeDate(year: Int, month: Int, day: Int): Instant {
        val zone = ZoneId.systemDefault()
        return LocalDate.of(year, month, day).atStartOfDay(zone).toInstant()
    }

    private fun makeDTO(id: String, czName: String, date: Instant) = FoodConsumedDTO(
        id = id,
        foodItemId = id,
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = czName,
        engName = "",
        weight = 100.0,
        date = date.epochSecond.toDouble(),
        calories = 150,
        protein = 0.0,
        carbohydrate = 0.0,
        carbohydrateSugar = 0.0,
        fat = 0.0,
        fatUnsaturated = 0.0,
        fiber = 0.0,
        salt = 0.0,
    )
}
