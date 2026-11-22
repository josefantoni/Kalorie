package antoni.kalorie.core.auth

import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.AppSettingsDTO
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.DailyGoalsDTO
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.MyCreatedMealIngredientDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class PendingMergeSnapshotStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // MARK: - Tests

    @Test
    fun load_withNoSavedSnapshot_returnsNull() {
        assertNull(makeSUT().load())
    }

    @Test
    fun saveThenLoad_returnsEqualSnapshot() {
        val sut = makeSUT()

        sut.save(makeSnapshot(sourceUserId = "anon-1", foodIds = listOf("f1", "f2")))

        val loaded = sut.load()
        assertEquals("anon-1", loaded?.sourceAnonymousUserId)
        assertEquals(listOf("f1", "f2"), loaded?.foodConsumed?.map { it.id })
    }

    @Test
    fun delete_removesSnapshot() {
        val sut = makeSUT()
        sut.save(makeSnapshot(sourceUserId = "anon-1", foodIds = listOf("f1")))

        sut.delete()

        assertNull(sut.load())
    }

    @Test
    fun delete_whenNoSnapshotExists_doesNotThrow() {
        makeSUT().delete()
    }

    @Test
    fun save_overwritesPreviousSnapshot() {
        val sut = makeSUT()
        sut.save(makeSnapshot(sourceUserId = "first", foodIds = listOf("f1")))

        sut.save(makeSnapshot(sourceUserId = "second", foodIds = listOf("f2")))

        assertEquals("second", sut.load()?.sourceAnonymousUserId)
    }

    @Test
    fun load_whenOptionalCollectionsAreMissingFromTheFile_defaultsThemToEmpty() {
        val directory = temporaryFolder.newFolder()
        java.io.File(directory, "pending-merge.json").writeText("""{"sourceAnonymousUserId":"anon-1","foodConsumed":[]}""")

        val loaded = PendingMergeSnapshotStore(directory).load()

        assertEquals(emptyList<Any>(), loaded?.favouriteFoods)
        assertEquals(emptyList<Any>(), loaded?.myCreatedMeals)
        assertEquals(emptyList<Any>(), loaded?.foodItemPortions)
    }

    @Test
    fun load_snapshotWrittenBeforeBodyWeights_stillDecodes_soAPendingMergeCanFinish() {
        val directory = temporaryFolder.newFolder()
        java.io.File(directory, "pending-merge.json").writeText(
            """{"sourceAnonymousUserId":"anon-1","foodConsumed":[],"favouriteFoods":[],"myCreatedMeals":[],"foodItemPortions":[]}""",
        )

        val loaded = checkNotNull(PendingMergeSnapshotStore(directory).load())

        assertEquals("anon-1", loaded.sourceAnonymousUserId)
        assertTrue(loaded.bodyWeights.isEmpty())
        assertNull(loaded.appSettings)
        assertNull(loaded.dailyGoals)
    }

    @Test
    fun load_snapshotWrittenBeforeSlovakNames_stillDecodes_withTheFallbackName() {
        val directory = temporaryFolder.newFolder()
        val sut = PendingMergeSnapshotStore(directory)
        sut.save(makeSnapshot(sourceUserId = "anon-1", foodIds = listOf("f1")))
        val file = java.io.File(directory, "pending-merge.json")
        file.writeText(file.readText().replace(Regex(""","sk_name":"[^"]*\""""), ""))

        val loaded = checkNotNull(sut.load())

        assertEquals("Test", loaded.foodConsumed.single().skName)
    }

    @Test
    fun load_fillsAMissingSlovakNameOfFavouriteFoods() {
        val snapshot = makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(favouriteFoods = listOf(makeFavouriteFood()))

        val loaded = loadingWithoutSlovakNames(snapshot)

        assertEquals("Mléko", loaded.favouriteFoods.single().skName)
    }

    @Test
    fun load_fillsAMissingSlovakNameOfIngredientsOfCreatedMeals() {
        val snapshot = makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(myCreatedMeals = listOf(makeCreatedMeal()))

        val loaded = loadingWithoutSlovakNames(snapshot)

        assertEquals("Ovesné vločky", loaded.myCreatedMeals.single().ingredients.single().skName)
    }

    @Test
    fun load_fillsAMissingSlovakNameOfIngredientsFromTheFirstNonBlankName() {
        val ingredient = makeCreatedMeal().ingredients.single().copy(czName = "", engName = "Oat")
        val snapshot = makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(myCreatedMeals = listOf(makeCreatedMeal().copy(ingredients = listOf(ingredient))))

        val loaded = loadingWithoutSlovakNames(snapshot)

        assertEquals("Oat", loaded.myCreatedMeals.single().ingredients.single().skName)
    }

    @Test
    fun load_keepsAnExistingSlovakName() {
        val sut = makeSUT()
        val snapshot = makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(
            favouriteFoods = listOf(makeFavouriteFood().copy(skName = "Mlieko")),
            myCreatedMeals = listOf(makeCreatedMeal().copy(ingredients = listOf(makeCreatedMeal().ingredients.single().copy(skName = "Ovsené vločky")))),
        )
        sut.save(snapshot)

        val loaded = checkNotNull(sut.load())

        assertEquals("Mlieko", loaded.favouriteFoods.single().skName)
        assertEquals("Ovsené vločky", loaded.myCreatedMeals.single().ingredients.single().skName)
    }

    @Test
    fun saveAndLoad_roundTripsBodyWeightsAndSettings() {
        val sut = makeSUT()
        sut.save(
            makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(
                bodyWeights = listOf(BodyWeightDTO(BodyWeightDomain(dayKey = "2026-10-27", kilograms = 78.44))),
                appSettings = AppSettingsDTO(AppSettingsDomain(showBodyWeight = false, bodyWeightUnit = BodyWeightUnit.LB)),
            ),
        )

        val loaded = sut.load()

        assertEquals(listOf(78.44), loaded?.bodyWeights?.map { it.weightKg })
        assertEquals(BodyWeightUnit.LB, loaded?.appSettings?.asDomain()?.bodyWeightUnit)
    }

    @Test
    fun saveAndLoad_roundTripsDailyGoals() {
        val sut = makeSUT()
        val goals = DailyGoalHistory(listOf(DailyGoalDomain("2026-11-01", kcalGoal = 2000.0, proteinPercent = 20.0, carbsPercent = 50.0, fatPercent = 30.0)))
        sut.save(makeSnapshot(sourceUserId = "anon-1", foodIds = emptyList()).copy(dailyGoals = DailyGoalsDTO(goals)))

        assertEquals(goals, sut.load()?.dailyGoals?.asDomain())
    }

    // MARK: - Helpers

    private fun makeSUT(): PendingMergeSnapshotStore = PendingMergeSnapshotStore(temporaryFolder.newFolder())

    private fun loadingWithoutSlovakNames(snapshot: PendingMergeSnapshot): PendingMergeSnapshot {
        val directory = temporaryFolder.newFolder()
        val sut = PendingMergeSnapshotStore(directory)
        sut.save(snapshot)
        val file = java.io.File(directory, "pending-merge.json")
        val stripped = file.readText().replace(Regex(""","sk_name":"[^"]*\""""), "")
        assertFalse("the strip must really remove the field, or the test would pass without exercising the fill", stripped.contains("sk_name"))
        file.writeText(stripped)
        return checkNotNull(sut.load())
    }

    private fun makeFavouriteFood() = FavouriteFoodDTO(
        id = "fav-1",
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = "Mléko",
        skName = "Mlieko",
        engName = "Milk",
        weight = 100.0,
        date = Instant.now().epochSecond.toDouble(),
        caloriesPerHundredGrams = 50.0,
        fat = 1.0,
        fatUnsaturatedFattyAcids = 1.0,
        carbohydrate = 1.0,
        carbohydratePureSugar = 1.0,
        protein = 1.0,
        salt = 0.1,
        favouritedAt = Instant.now().epochSecond.toDouble(),
    )

    private fun makeCreatedMeal() = MyCreatedMealDTO(
        id = "meal-1",
        name = "Kaše",
        ingredients = listOf(
            MyCreatedMealIngredientDTO(
                foodItemId = "12345",
                czName = "Ovesné vločky",
                skName = "Ovsené vločky",
                engName = "Oats",
                grams = 50.0,
                caloriesPerHundredGrams = 155.0,
                fat = 10.0,
                fatUnsaturatedFattyAcids = 3.0,
                carbohydrate = 1.0,
                carbohydratePureSugar = 0.0,
                protein = 13.0,
                salt = 0.3,
            ),
        ),
        createdAt = Instant.now().epochSecond.toDouble(),
        updatedAt = Instant.now().epochSecond.toDouble(),
    )

    private fun makeSnapshot(sourceUserId: String, foodIds: List<String>) = PendingMergeSnapshot(
        sourceAnonymousUserId = sourceUserId,
        foodConsumed = foodIds.map { id ->
            FoodConsumedDTO(
                id = id,
                foodItemId = id,
                foodItemKind = FoodItemKind.CATALOGUE,
                czName = "Test",
                skName = "Test",
                engName = "Test",
                weight = 100.0,
                date = Instant.now().epochSecond.toDouble(),
                calories = 100,
                protein = 1.0,
                carbohydrate = 1.0,
                carbohydrateSugar = 1.0,
                fat = 1.0,
                fatUnsaturated = 1.0,
                fiber = 1.0,
                salt = 1.0,
            )
        },
    )
}
