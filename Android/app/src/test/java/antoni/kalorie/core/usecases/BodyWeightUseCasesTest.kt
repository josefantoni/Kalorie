package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BodyWeightUseCasesTest {

    // MARK: - Save

    @Test
    fun save_writesToDayKeyDocumentInUserCollection() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        val day = Instant.ofEpochSecond(1_792_000_000)
        sut(78.44, day)
        assertEquals(BodyWeightDayKey.make(day), dataProvider.setSavedId)
        assertEquals("users/user-1/bodyWeights", dataProvider.setSavedCollection)
        assertEquals(78.44, (dataProvider.setSavedItem as BodyWeightDTO).weightKg, 0.0)
    }

    @Test
    fun save_futureDay_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        val error = runCatching { sut(70.0, Instant.now().plusSeconds(3 * 86_400)) }.exceptionOrNull()
        assertEquals(BodyWeightError.FutureDay, error)
        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun save_outOfRange_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        val error = runCatching { sut(29.9, Instant.now()) }.exceptionOrNull()
        assertEquals(BodyWeightError.OutOfRange, error)
        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun save_notAuthenticated_throws() = runTest {
        val (sut, _) = makeSaveSUT(userId = null)
        assertEquals(AuthError.NotAuthenticated, runCatching { sut(70.0, Instant.now()) }.exceptionOrNull())
    }

    // MARK: - Export range

    @Test
    fun fetchForRange_oneQueryUpToLastDay_limitCoversTheDaysPlusTheLastEarlierEntry() = runTest {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchBodyWeightsUseCase(dataProvider, AuthProviderFake(userId = "user-1"))
        val from = checkNotNull(BodyWeightDayKey.date("2026-09-01"))
        val to = checkNotNull(BodyWeightDayKey.date("2026-09-10"))

        sut.between(from, to)

        assertEquals(listOf(Triple("users/user-1/bodyWeights", "2026-09-10", 11)), dataProvider.lessThanOrEqualQueries)
    }

    @Test
    fun fetchAfter_queriesNewerDaysOldestFirstWithTheLaterLimit() = runTest {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchBodyWeightsUseCase(dataProvider, AuthProviderFake(userId = "user-1"))

        sut.after(checkNotNull(BodyWeightDayKey.date("2026-09-10")))

        assertEquals(listOf(Triple("users/user-1/bodyWeights", "2026-09-10", 2)), dataProvider.greaterThanQueries)
    }

    @Test
    fun fetchInYear_readsOnlyThatCalendarYear_soNoOtherYearIsBilled() = runTest {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchBodyWeightsUseCase(dataProvider, AuthProviderFake(userId = "user-1"))

        sut.inYear(2025)

        assertEquals(listOf(Triple("users/user-1/bodyWeights", "2025-01-01", "2025-12-31")), dataProvider.rangeQueries)
    }

    // MARK: - Settings

    @Test
    fun settings_absentDocument_readsAsDefaults() = runTest {
        val sut = FetchAppSettingsUseCase(FirestoreDataProviderFake(), AuthProviderFake(userId = "user-1"))
        assertEquals(AppSettingsDomain.defaults, sut())
    }

    @Test
    fun settings_save_usesMergeNotSet() = runTest {
        val dataProvider = FirestoreDataProviderFake()
        val sut = SaveAppSettingsUseCase(dataProvider, AuthProviderFake(userId = "user-1"))
        sut(AppSettingsDomain(showBodyWeight = false, bodyWeightUnit = BodyWeightUnit.LB))
        assertEquals("app", dataProvider.mergedId)
        assertEquals("users/user-1/settings", dataProvider.mergedCollection)
        assertNull(dataProvider.setSavedId)
    }

    // MARK: - Helpers

    private fun makeSaveSUT(userId: String? = "user-1"): Pair<SaveBodyWeightUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return SaveBodyWeightUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
