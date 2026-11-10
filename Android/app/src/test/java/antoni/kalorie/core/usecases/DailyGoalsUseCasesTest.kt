package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.GoalActivityLevel
import antoni.kalorie.core.models.GoalEstimate
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.models.GoalSex
import antoni.kalorie.core.networking.DailyGoalEntryDTO
import antoni.kalorie.core.networking.DailyGoalsDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class DailyGoalsUseCasesTest {

    // MARK: - History

    @Test
    fun goalOnDay_usesLatestEntryNotAfterTheDay() {
        val history = DailyGoalHistory(
            listOf(DailyGoalDomain("2026-01-10", kcalGoal = 2000.0), DailyGoalDomain("2026-06-01", kcalGoal = 2400.0)),
        )
        assertNull(history.goal("2026-01-09"))
        assertEquals(2000.0, history.goal("2026-03-01")?.kcalGoal)
        assertEquals(2400.0, history.goal("2026-06-01")?.kcalGoal)
    }

    @Test
    fun goalOnDay_afterEndedEntry_isNilButEarlierDaysKeepTheirGoal() {
        val history = DailyGoalHistory(listOf(DailyGoalDomain("2026-01-10", kcalGoal = 2000.0), DailyGoalDomain.ended("2026-05-01")))
        assertEquals(2000.0, history.goal("2026-04-30")?.kcalGoal)
        assertNull(history.goal("2026-05-01"))
    }

    @Test
    fun replacing_sameDayReplacesEntry() {
        val history = DailyGoalHistory(listOf(DailyGoalDomain("2026-01-10", kcalGoal = 2000.0)))
            .replacing(DailyGoalDomain("2026-01-10", kcalGoal = 2200.0))
        assertEquals(1, history.entries.size)
        assertEquals(2200.0, history.entries.first().kcalGoal)
    }

    @Test
    fun replacing_dropsEntriesDatedAfterTheNewOne() {
        val history = DailyGoalHistory(listOf(DailyGoalDomain("2026-01-12", kcalGoal = 1800.0)))
            .replacing(DailyGoalDomain("2026-01-11", kcalGoal = 2200.0))
        assertEquals(1, history.entries.size)
        assertEquals(2200.0, history.goal("2026-01-12")?.kcalGoal)
    }

    @Test
    fun replacing_endedGoalDropsLaterEntries() {
        val history = DailyGoalHistory(listOf(DailyGoalDomain("2026-01-12", kcalGoal = 1800.0)))
            .replacing(DailyGoalDomain.ended("2026-01-11"))
        assertNull(history.goal("2026-01-12"))
    }

    @Test
    fun replacing_keepsEarlierEntries() {
        val history = DailyGoalHistory(listOf(DailyGoalDomain("2026-01-05", kcalGoal = 2000.0)))
            .replacing(DailyGoalDomain("2026-01-11", kcalGoal = 2200.0))
        assertEquals(listOf("2026-01-05", "2026-01-11"), history.entries.map { it.validFrom })
    }

    // MARK: - Estimate

    @Test
    fun estimate_matchesOwnerSanityCheck() {
        val estimate = checkNotNull(GoalEstimate.make(GoalSex.MALE, 1991, 188.0, 92.0, GoalActivityLevel.SEDENTARY, 2026))
        assertEquals(1925.0, estimate.basalRate.toDouble(), 1.0)
        assertEquals(2310.0, estimate.expenditure.toDouble(), 2.0)
        assertEquals(listOf(-10, -5, 0, 5, 10), estimate.variants.map { it.percentChange })
        assertEquals(estimate.expenditure, estimate.variants[2].kcal)
    }

    @Test
    fun estimate_missingInput_isNil() {
        assertNull(GoalEstimate.make(null, 1991, 188.0, 92.0, GoalActivityLevel.LIGHT, 2026))
    }

    // MARK: - Wire format

    @Test
    fun dto_roundTripsPercentGoalWithCalculatorInputs() {
        val goal = DailyGoalDomain(
            validFrom = "2026-11-06",
            kcalGoal = 2100.0,
            macroMode = GoalMacroMode.PERCENT,
            proteinPercent = 20.0,
            carbsPercent = 50.0,
            fatPercent = 30.0,
            sex = GoalSex.FEMALE,
            birthYear = 1990,
            activityLevel = GoalActivityLevel.VERY_HIGH,
        )
        val history = DailyGoalHistory(listOf(goal))
        val json = Json.encodeToString(DailyGoalsDTO.serializer(), DailyGoalsDTO(history))
        assertEquals(history, decode(json).asDomain())
    }

    @Test
    fun dto_wrongTypedFieldReadsAsAbsentAndKeepsTheEntry() {
        val json = """{"goals":[
            {"valid_from":"2026-01-01","kcal_goal":2000,"birth_year":1990.5,"weight_kg":80},
            {"valid_from":"2026-02-01","kcal_goal":1800}]}"""
        val history = decode(json).asDomain()
        assertEquals(listOf("2026-01-01", "2026-02-01"), history.entries.map { it.validFrom })
        assertEquals(2000.0, history.entries[0].kcalGoal)
        assertNull(history.entries[0].birthYear)
        assertEquals(80.0, history.entries[0].weightKg)
        assertEquals(1800.0, history.entries[1].kcalGoal)
    }

    @Test
    fun dto_birthYearStoredAsAWholeDoubleIsRead() {
        val json = """{"goals":[{"valid_from":"2026-01-01","kcal_goal":2000,"birth_year":1990.0}]}"""
        assertEquals(1990, decode(json).asDomain().entries[0].birthYear)
    }

    @Test
    fun dto_unreadableEntryIsSkippedAndTheRestSurvives() {
        val history = decode("""{"goals":["oops",{"valid_from":"2026-02-01","kcal_goal":1800}]}""").asDomain()
        assertEquals(listOf("2026-02-01"), history.entries.map { it.validFrom })
    }

    @Test
    fun dto_nonArrayGoalsReadsAsEmptyHistory() {
        assertTrue(decode("""{"goals":"oops"}""").asDomain().entries.isEmpty())
    }

    @Test
    fun dto_unknownEnumValuesReadAsAbsent() {
        val decoded = decodeEntry(mapOf("sex" to JsonPrimitive("other"), "activity_level" to JsonPrimitive("extreme")))
        assertNull(decoded.asDomain()?.sex)
        assertNull(decoded.asDomain()?.activityLevel)
    }

    @Test
    fun dto_unknownMacroModeSkipsTheEntryInsteadOfReadingAsPercent() {
        assertNull(decodeEntry(mapOf("macro_mode" to JsonPrimitive("mixed"))).asDomain())
    }

    @Test
    fun dto_wrongTypedMacroModeSkipsTheEntryInsteadOfReadingAsPercent() {
        assertNull(decodeEntry(mapOf("macro_mode" to JsonPrimitive(5))).asDomain())
    }

    @Test
    fun dto_wrongTypedKcalGoalSkipsTheEntryAndThePreviousGoalStays() {
        val json = """{"goals":[
            {"valid_from":"2026-01-01","kcal_goal":2000},
            {"valid_from":"2026-02-01","kcal_goal":"1800"}]}"""
        val history = decode(json).asDomain()
        assertEquals(listOf("2026-01-01"), history.entries.map { it.validFrom })
        assertEquals(2000.0, history.goal("2026-03-01")?.kcalGoal)
    }

    @Test
    fun dto_explicitNullKcalGoalStillReadsAsEndedGoal() {
        val history = decode("""{"goals":[{"valid_from":"2026-02-01","kcal_goal":null}]}""").asDomain()
        assertEquals(listOf("2026-02-01"), history.entries.map { it.validFrom })
        assertNull(history.entries[0].kcalGoal)
    }

    @Test
    fun dto_endedGoalWithoutMacroModeStillReads() {
        assertNotNull(DailyGoalEntryDTO(DailyGoalDomain.ended("2026-11-06")).asDomain())
    }

    // MARK: - Save

    @Test
    fun save_stampsTodayAndMergesIntoGoalsDocument() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        sut(percentGoal(2000.0))
        assertEquals("goals", dataProvider.mergedId)
        assertEquals("users/user-1/settings", dataProvider.mergedCollection)
        assertEquals("2026-11-06", (dataProvider.mergedItem as DailyGoalsDTO).asDomain().entries.first().validFrom)
    }

    @Test
    fun save_secondSaveSameDay_replacesEntry() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        dataProvider.stubbedDocument = dto(DailyGoalDomain("2026-11-06", kcalGoal = 1800.0))
        val history = sut(percentGoal(2000.0))
        assertEquals(1, history.entries.size)
        assertEquals(2000.0, history.entries.first().kcalGoal)
    }

    @Test
    fun save_keepsEarlierEntries() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        dataProvider.stubbedDocument = dto(DailyGoalDomain("2026-01-01", kcalGoal = 1800.0))
        val history = sut(percentGoal(2000.0))
        assertEquals(listOf("2026-01-01", "2026-11-06"), history.entries.map { it.validFrom })
    }

    @Test
    fun save_percentNotSummingTo100_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        assertThrows(sut, percentGoal(2000.0).copy(fatPercent = 40.0), DailyGoalError.InvalidMacros)
        assertNull(dataProvider.mergedId)
    }

    @Test
    fun save_percentModeWithNegativeMacro_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        val goal = percentGoal(2000.0).copy(proteinPercent = 120.0, carbsPercent = -20.0, fatPercent = 0.0)
        assertThrows(sut, goal, DailyGoalError.InvalidMacros)
        assertNull(dataProvider.mergedId)
    }

    @Test
    fun save_gramsModeWithNegativeMacro_throws() = runTest {
        val (sut, _) = makeSaveSUT()
        assertThrows(sut, gramsGoal(protein = 150.0, carbs = -10.0, fat = 60.0), DailyGoalError.InvalidMacros)
    }

    @Test
    fun save_gramsModeAboveLimit_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        assertThrows(sut, gramsGoal(protein = 99_999_999.0, carbs = 200.0, fat = 60.0), DailyGoalError.InvalidMacros)
        assertNull(dataProvider.mergedId)
    }

    @Test
    fun save_gramsModeAtLimit_saves() = runTest {
        val (sut, _) = makeSaveSUT()
        sut(gramsGoal(protein = 1000.0, carbs = 200.0, fat = 60.0))
    }

    @Test
    fun profileEntry_skipsEntriesWithoutProfile() {
        val withProfile = DailyGoalDomain("2026-01-01", kcalGoal = 2000.0, birthYear = 1990)
        val history = DailyGoalHistory(listOf(withProfile, DailyGoalDomain.ended("2026-02-01")))
        assertEquals("2026-01-01", history.latestProfileEntry("2026-03-01")?.validFrom)
        assertNull(history.latestProfileEntry("2025-12-31"))
    }

    @Test
    fun save_kcalOutOfRange_throws() = runTest {
        val (sut, _) = makeSaveSUT()
        assertThrows(sut, percentGoal(100.0), DailyGoalError.InvalidKcal)
    }

    @Test
    fun save_endedGoal_writesEntryWithoutKcal() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        dataProvider.stubbedDocument = dto(DailyGoalDomain("2026-01-01", kcalGoal = 1800.0))
        val history = sut(DailyGoalDomain.ended(""))
        assertNull(history.goal("2026-11-06"))
        assertEquals(1800.0, history.goal("2026-11-05")?.kcalGoal)
    }

    @Test
    fun save_endedGoal_onTheDayOfASavedGoal_keepsTheProfile() = runTest {
        val (sut, dataProvider) = makeSaveSUT()
        val saved = DailyGoalDomain(
            validFrom = "2026-11-06",
            kcalGoal = 1800.0,
            sex = GoalSex.FEMALE,
            birthYear = 1990,
            heightCm = 170.0,
            weightKg = 65.0,
            activityLevel = GoalActivityLevel.MODERATE,
        )
        dataProvider.stubbedDocument = dto(saved)
        val history = sut(DailyGoalDomain.ended(""))
        assertNull(history.goal("2026-11-06"))
        val profile = checkNotNull(history.latestProfileEntry("2026-11-06"))
        assertEquals(1990, profile.birthYear)
        assertEquals(170.0, profile.heightCm)
        assertEquals(GoalActivityLevel.MODERATE, profile.activityLevel)
        assertNull(profile.kcalGoal)
    }

    @Test
    fun save_unauthenticated_throws() = runTest {
        val (sut, _) = makeSaveSUT(userId = null)
        assertEquals(AuthError.NotAuthenticated, runCatching { sut(percentGoal(2000.0)) }.exceptionOrNull())
    }

    // MARK: - Helpers

    private fun decode(json: String): DailyGoalsDTO = Json.decodeFromString(DailyGoalsDTO.serializer(), json)

    private fun decodeEntry(overrides: Map<String, kotlinx.serialization.json.JsonElement>): DailyGoalEntryDTO {
        val base = mapOf("valid_from" to JsonPrimitive("2026-11-06"), "kcal_goal" to JsonPrimitive(2000), "macro_mode" to JsonPrimitive("percent"))
        return DailyGoalEntryDTO.decode(JsonObject(base + overrides))
    }

    private fun dto(vararg entries: DailyGoalDomain) = DailyGoalsDTO(DailyGoalHistory(entries.toList()))

    private fun percentGoal(kcal: Double) = DailyGoalDomain(
        validFrom = "",
        kcalGoal = kcal,
        macroMode = GoalMacroMode.PERCENT,
        proteinPercent = 20.0,
        carbsPercent = 50.0,
        fatPercent = 30.0,
    )

    private fun gramsGoal(protein: Double, carbs: Double, fat: Double) = DailyGoalDomain(
        validFrom = "",
        kcalGoal = 2000.0,
        macroMode = GoalMacroMode.GRAMS,
        proteinGrams = protein,
        carbsGrams = carbs,
        fatGrams = fat,
    )

    private suspend fun assertThrows(sut: SaveDailyGoalUseCase, goal: DailyGoalDomain, expected: DailyGoalError) {
        val error = runCatching { sut(goal) }.exceptionOrNull() ?: return fail("Expected $expected")
        assertEquals(expected, error)
    }

    private fun makeSaveSUT(userId: String? = "user-1"): Pair<SaveDailyGoalUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val authProvider = AuthProviderFake(userId = userId)
        val sut = SaveDailyGoalUseCase(
            fetchDailyGoals = FetchDailyGoalsUseCase(dataProvider, authProvider),
            dataProvider = dataProvider,
            authProvider = authProvider,
            now = { Instant.ofEpochSecond(1_793_950_400) },
        )
        return sut to dataProvider
    }
}
