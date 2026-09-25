package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodsConsumedInRangeUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchInRange_includesTheWholeLastDayAndExcludesTheNextOne() = runTest {
        val (sut, dataProvider) = makeSUT()
        val dtos = listOf(
            makeDTO("before", makeDate(day = 4, hour = 23, minute = 59)),
            makeDTO("firstMorning", makeDate(day = 5, hour = 0, minute = 0)),
            makeDTO("lastNight", makeDate(day = 7, hour = 23, minute = 59)),
            makeDTO("after", makeDate(day = 8, hour = 0, minute = 0)),
        )
        dataProvider.stubbedRangeDocuments = { lower, upper -> dtos.filter { it.date >= lower && it.date < upper } }

        val result = sut(from = makeDate(day = 5, hour = 15), to = makeDate(day = 7, hour = 9))

        assertEquals(setOf("firstMorning", "lastNight"), result.map { it.id }.toSet())
    }

    @Test
    fun fetchInRange_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(from = Instant.now(), to = Instant.now())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchFoodsConsumedInRangeUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodsConsumedInRangeUseCase(dataProvider, AuthProviderFake(userId = userId), ZONE) to dataProvider
    }

    private fun makeDate(day: Int, hour: Int = 0, minute: Int = 0): Instant =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, ZONE).toInstant()

    private fun makeDTO(id: String, date: Instant) = FoodConsumedDTO(
        id = id,
        foodItemId = id,
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = id,
        engName = "",
        weight = 100.0,
        date = date.epochSecond.toDouble(),
        calories = 100,
        protein = 0.0,
        carbohydrate = 0.0,
        carbohydrateSugar = 0.0,
        fat = 0.0,
        fatUnsaturated = 0.0,
        salt = 0.0,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}
