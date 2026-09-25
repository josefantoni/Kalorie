package antoni.kalorie.core.auth

import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FoodConsumedDTO
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

    // MARK: - Helpers

    private fun makeSUT(): PendingMergeSnapshotStore = PendingMergeSnapshotStore(temporaryFolder.newFolder())

    private fun makeSnapshot(sourceUserId: String, foodIds: List<String>) = PendingMergeSnapshot(
        sourceAnonymousUserId = sourceUserId,
        foodConsumed = foodIds.map { id ->
            FoodConsumedDTO(
                id = id,
                foodItemId = id,
                foodItemKind = FoodItemKind.CATALOGUE,
                czName = "Test",
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
