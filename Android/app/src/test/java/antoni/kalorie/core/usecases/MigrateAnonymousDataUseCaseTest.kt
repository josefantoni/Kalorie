package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodPortionDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MigrateAnonymousDataUseCaseTest {

    // MARK: - Tests

    @Test
    fun migrate_carriesFavouriteFoodsThroughWithSameLifecycleAsFoodConsumed() = runTest {
        val sut = makeSUT()
        sut.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.favouriteFoods("anon-1") to listOf(makeFavourite("fav1")))

        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(
            "an anonymous user's favourites must survive sign-in exactly like their logged food",
            listOf("fav1"),
            sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.favouriteFoods("target-1")]?.map { it.second },
        )
    }

    @Test
    fun migrate_carriesMyCreatedMealsThroughWithSameLifecycleAsFoodConsumed() = runTest {
        val sut = makeSUT()
        sut.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.myCreatedMeals("anon-1") to listOf(makeMeal("meal1")))

        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(
            "an anonymous user's created meals must survive sign-in exactly like their logged food",
            listOf("meal1"),
            sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.myCreatedMeals("target-1")]?.map { it.second },
        )
    }

    @Test
    fun migrate_carriesFoodItemPortionsThroughWithSameLifecycleAsFoodConsumed() = runTest {
        val sut = makeSUT()
        sut.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.foodItemPortions("anon-1") to listOf(makePortions("12345678")))

        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(
            "an anonymous user's personal portions must survive sign-in exactly like their logged food",
            listOf("12345678"),
            sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.foodItemPortions("target-1")]?.map { it.second },
        )
    }

    @Test
    fun migrate_signsInWithCredentialAfterSavingSnapshot() = runTest {
        val sut = makeSUT()
        sut.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.foodConsumed("anon-1") to listOf(makeFood("f1")))

        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(1, sut.authCommandProvider.signInCallCount)
        assertNull("the snapshot must be deleted once the merge is done", sut.snapshotStore.stubbedSnapshot)
    }

    @Test
    fun migrate_withEmptySource_writesNoItemsAndCleansUp() = runTest {
        val sut = makeSUT()

        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(0, sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.foodConsumed("target-1")]?.size)
        assertEquals(1, sut.snapshotStore.deleteCallCount)
    }

    @Test
    fun migrate_runTwice_isIdempotent() = runTest {
        val sut = makeSUT()
        sut.dataProvider.stubbedDocumentsByCollection = mapOf(
            Constants.Firestore.foodConsumed("anon-1") to listOf(makeFood("f1"), makeFood("f2")),
        )

        sut.useCase.migrate("anon-1", makeCredential())
        sut.useCase.migrate("anon-1", makeCredential())

        assertEquals(
            "writing the same ids again overwrites rather than duplicates",
            2,
            sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.foodConsumed("target-1")]?.size,
        )
    }

    @Test
    fun resumeIfNeeded_withNoSnapshot_doesNothing() = runTest {
        val sut = makeSUT()

        sut.useCase.resumeIfNeeded()

        assertEquals(emptyMap<String, Any>(), sut.dataProvider.batchSavedItemsByCollection)
        assertEquals(0, sut.authCommandProvider.signInCallCount)
    }

    @Test
    fun resumeIfNeeded_whenStillOnSourceUid_discardsSnapshotWithoutWriting() = runTest {
        val sut = makeSUT(currentUserId = "anon-1")
        sut.snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = listOf(makeFood("f1")))

        sut.useCase.resumeIfNeeded()

        assertEquals(emptyMap<String, Any>(), sut.dataProvider.batchSavedItemsByCollection)
        assertEquals(1, sut.snapshotStore.deleteCallCount)
    }

    @Test
    fun resumeIfNeeded_whenAlreadyOnTargetUid_finishesWriteAndCleansUp() = runTest {
        val sut = makeSUT(currentUserId = "target-1")
        sut.snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = listOf(makeFood("f1")))

        sut.useCase.resumeIfNeeded()

        assertEquals(1, sut.dataProvider.batchSavedItemsByCollection[Constants.Firestore.foodConsumed("target-1")]?.size)
        assertEquals(1, sut.snapshotStore.deleteCallCount)
    }

    // MARK: - Helpers

    private class Fixture(
        val useCase: MigrateAnonymousDataUseCase,
        val dataProvider: FirestoreDataProviderFake,
        val authCommandProvider: AuthCommandProviderFake,
        val snapshotStore: PendingMergeSnapshotStoreFake,
    )

    private fun makeSUT(currentUserId: String? = "target-1"): Fixture {
        val dataProvider = FirestoreDataProviderFake()
        val authCommandProvider = AuthCommandProviderFake()
        val snapshotStore = PendingMergeSnapshotStoreFake()
        val useCase = MigrateAnonymousDataUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = currentUserId),
            authCommandProvider = authCommandProvider,
            snapshotStore = snapshotStore,
        )
        return Fixture(useCase, dataProvider, authCommandProvider, snapshotStore)
    }

    private fun makeCredential(): AuthCredential = GoogleAuthProvider.getCredential("id-token", null)

    private fun makeFood(id: String) = FoodConsumedDTO(
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

    private fun makeFavourite(id: String) = FavouriteFoodDTO(
        item = FoodItemDomain(
            id = id,
            kind = FoodItemKind.CATALOGUE,
            czName = "Test",
            engName = "Test",
            weight = 100.0,
            date = Instant.now(),
            energyKJ = 400.0,
            caloriesPerHundredGrams = 100.0,
            fat = 1.0,
            fatSaturated = 1.0,
            fatUnsaturatedFattyAcids = 1.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 1.0,
            fiber = 1.0,
            protein = 1.0,
            salt = 1.0,
        ),
        favouritedAt = Instant.now(),
    )

    private fun makeMeal(id: String) = MyCreatedMealDTO(
        MyCreatedMealDomain(
            id = id,
            name = "Test",
            ingredients = listOf(
                MyCreatedMealIngredientDomain(
                    foodItemId = "12345",
                    czName = "Test",
                    engName = "Test",
                    grams = 50.0,
                    nutrition = FoodNutritionValues(
                        energyKJ = 400.0,
                        caloriesPerHundredGrams = 100.0,
                        fat = 1.0,
                        fatSaturated = 1.0,
                        fatUnsaturatedFattyAcids = 1.0,
                        carbohydrate = 1.0,
                        carbohydratePureSugar = 1.0,
                        fiber = 1.0,
                        protein = 1.0,
                        salt = 1.0,
                    ),
                ),
            ),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ),
    )

    private fun makePortions(id: String) = FoodItemPersonalPortionsDTO(
        id = id,
        portions = listOf(FoodPortionDTO(FoodPortionDomain(name = "1 balení", grams = 33.0))),
    )
}
