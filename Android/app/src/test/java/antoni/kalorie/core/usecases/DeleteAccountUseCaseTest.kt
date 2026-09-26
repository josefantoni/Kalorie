package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.FoodPortionDTO
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteAccountUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_deletesFavouriteFoodsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.favouriteFoods(USER_ID) to listOf(makeFavourite("fav1")))

        fixture.sut()

        assertEquals(
            "Firestore does not cascade, so favourites are user data and must not survive account deletion as orphans",
            listOf("fav1"),
            fixture.deleted(Constants.Firestore.favouriteFoods(USER_ID)),
        )
    }

    @Test
    fun invoke_deletesMyCreatedMealsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.myCreatedMeals(USER_ID) to listOf(makeMeal("meal1")))

        fixture.sut()

        assertEquals(listOf("meal1"), fixture.deleted(Constants.Firestore.myCreatedMeals(USER_ID)))
    }

    @Test
    fun invoke_deletesFoodItemPortionsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(
            Constants.Firestore.foodItemPortions(USER_ID) to listOf(
                FoodItemPersonalPortionsDTO(id = "12345678", portions = listOf(FoodPortionDTO(FoodPortionDomain(name = "1 balení", grams = 33.0)))),
            ),
        )

        fixture.sut()

        assertEquals(listOf("12345678"), fixture.deleted(Constants.Firestore.foodItemPortions(USER_ID)))
    }

    @Test
    fun invoke_deletesOwnSubmissionsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedByFieldByCollection = mapOf(Constants.Firestore.FOOD_ITEM_SUBMISSIONS to listOf(makeSubmission("sub1")))

        fixture.sut()

        assertEquals(
            "a pending or rejected submission is the user's own data and must not survive account deletion",
            listOf("sub1"),
            fixture.deleted(Constants.Firestore.FOOD_ITEM_SUBMISSIONS),
        )
    }

    @Test
    fun invoke_deletesOwnReportsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedByFieldByCollection = mapOf(
            Constants.Firestore.FOOD_ITEM_REPORTS to listOf(
                FoodItemReportDTO(barcode = "12345678", reportedBy = USER_ID, reason = "wrong calories", reportedAt = Instant.now()),
            ),
        )

        fixture.sut()

        assertEquals(
            "a report is the user's own data and must not survive account deletion",
            listOf("12345678_$USER_ID"),
            fixture.deleted(Constants.Firestore.FOOD_ITEM_REPORTS),
        )
    }

    @Test
    fun invoke_deletesFirestoreDataBeforeDeletingAuthAccount() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(
            Constants.Firestore.mealTypes(USER_ID) to listOf(MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 0, endMinutes = 60)),
            Constants.Firestore.foodConsumed(USER_ID) to listOf(makeFood("f1")),
        )

        fixture.sut()

        assertEquals(1, fixture.authCommandProvider.deleteCallCount)
        assertEquals(listOf("0"), fixture.deletedBeforeAccountDeletion[Constants.Firestore.mealTypes(USER_ID)])
        assertEquals(listOf("f1"), fixture.deletedBeforeAccountDeletion[Constants.Firestore.foodConsumed(USER_ID)])
    }

    @Test
    fun invoke_deletesTheUserProfileDocumentLast() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf(makeFood("f1")))

        fixture.sut()

        assertEquals(listOf(USER_ID), fixture.deleted(Constants.Firestore.USERS))
        assertEquals(
            "a failure part-way must leave the profile in place so the account is not half-erased",
            listOf(Constants.Firestore.foodConsumed(USER_ID), Constants.Firestore.USERS),
            fixture.dataProvider.deletionCollectionOrder,
        )
    }

    @Test
    fun invoke_whenNotAuthenticated_throwsAuthError() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(userId = null))

        try {
            fixture.sut()
            fail("Expected AuthError to be thrown")
        } catch (_: AuthError) {
        }
    }

    @Test
    fun invoke_whenLastSignInIsStale_throwsBeforeDeletingAnyData() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(userId = USER_ID, lastSignInDate = Instant.now().minusSeconds(10 * 60)))
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf(makeFood("f1")))
        fixture.snapshotStore.stubbedSnapshot = makeSnapshot()

        try {
            fixture.sut()
            fail("Expected DeleteAccountError.RequiresRecentLogin to be thrown")
        } catch (error: DeleteAccountError.RequiresRecentLogin) {
            assertNotNull("a rejected attempt must leave everything intact so the user can simply retry", fixture.snapshotStore.stubbedSnapshot)
            assertTrue(
                "a stale session must be rejected before any data is deleted, otherwise the account survives but its history does not",
                fixture.dataProvider.deletedIdsByCollection.isEmpty(),
            )
            assertFalse("nothing was deleted yet, so a retry must not skip the wipe", error.dataAlreadyDeleted)
        }
    }

    @Test
    fun invoke_whenRequiresRecentLogin_throwsTypedErrorFlaggingDataAlreadyDeleted() = runTest {
        val fixture = makeSUT()
        fixture.authCommandProvider.deleteError = FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "stale")

        try {
            fixture.sut()
            fail("Expected DeleteAccountError.RequiresRecentLogin to be thrown")
        } catch (error: DeleteAccountError.RequiresRecentLogin) {
            assertTrue("the wipe already ran by the time deleteCurrentUser() is reached, so a retry must skip it", error.dataAlreadyDeleted)
        }
    }

    @Test
    fun invoke_whenSkipDataWipeIsTrue_doesNotReReadAlreadyWipedCollections() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(
            Constants.Firestore.mealTypes(USER_ID) to listOf(MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 0, endMinutes = 60)),
        )

        fixture.sut(skipDataWipe = true)

        assertTrue("a retry that already wiped the data must not re-read and re-delete it", fixture.dataProvider.deletedIdsByCollection.isEmpty())
        assertEquals(1, fixture.authCommandProvider.deleteCallCount)
    }

    @Test
    fun invoke_discardsPendingMergeSnapshot() = runTest {
        val fixture = makeSUT()
        fixture.snapshotStore.stubbedSnapshot = makeSnapshot()

        fixture.sut()

        assertNull(
            "a snapshot surviving the deletion would be resumed into the fresh anonymous account on the next launch",
            fixture.snapshotStore.stubbedSnapshot,
        )
    }

    @Test
    fun invoke_whenSkipDataWipeIsTrue_stillDiscardsPendingMergeSnapshot() = runTest {
        val fixture = makeSUT()
        fixture.snapshotStore.stubbedSnapshot = makeSnapshot()

        fixture.sut(skipDataWipe = true)

        assertNull(
            "the first attempt may have failed before reaching the snapshot, so the retry must still clear it",
            fixture.snapshotStore.stubbedSnapshot,
        )
    }

    @Test
    fun invoke_whenSnapshotDeleteFails_deletesNothingElse() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf(makeFood("f1")))
        fixture.snapshotStore.deleteError = IllegalStateException("disk")

        try {
            fixture.sut()
            fail("Expected the snapshot error to be thrown")
        } catch (_: IllegalStateException) {
            assertTrue(
                "the account must survive intact when the snapshot cannot be cleared, so the user can retry",
                fixture.dataProvider.deletedIdsByCollection.isEmpty(),
            )
            assertEquals(0, fixture.authCommandProvider.deleteCallCount)
        }
    }

    @Test
    fun invoke_whenDeleteFailsWithOtherError_propagatesError() = runTest {
        val fixture = makeSUT()
        fixture.authCommandProvider.deleteError = RuntimeException("offline")

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: DeleteAccountError.RequiresRecentLogin) {
            fail("Did not expect RequiresRecentLogin")
        } catch (_: RuntimeException) {
        }
    }

    // MARK: - Helpers

    private class Fixture(
        val dataProvider: FirestoreDataProviderFake,
        val authCommandProvider: AuthCommandProviderFake,
        val snapshotStore: PendingMergeSnapshotStoreFake,
        val sut: DeleteAccountUseCase,
        val snapshots: MutableList<Map<String, List<String>>>,
    ) {
        val deletedBeforeAccountDeletion: Map<String, List<String>>
            get() = snapshots.firstOrNull().orEmpty()

        fun deleted(collection: String): List<String> = dataProvider.deletedIdsByCollection[collection].orEmpty()
    }

    private fun makeSUT(authProvider: AuthProviderFake = AuthProviderFake(userId = USER_ID)): Fixture {
        val dataProvider = FirestoreDataProviderFake()
        val snapshots = mutableListOf<Map<String, List<String>>>()
        val authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.onDelete = { snapshots += dataProvider.deletedIdsByCollection }
        val snapshotStore = PendingMergeSnapshotStoreFake()
        val sut = DeleteAccountUseCase(
            dataProvider = dataProvider,
            authProvider = authProvider,
            authCommandProvider = authCommandProvider,
            snapshotStore = snapshotStore,
        )
        return Fixture(dataProvider, authCommandProvider, snapshotStore, sut, snapshots)
    }

    private fun makeSnapshot() = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = emptyList())

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

    private fun makeItem(id: String) = FoodItemDomain(
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
    )

    private fun makeFavourite(id: String) = FavouriteFoodDTO(item = makeItem(id), favouritedAt = Instant.now())

    private fun makeSubmission(id: String) = FoodItemSubmissionDTO(
        id = id,
        barcode = "12345678",
        submittedBy = USER_ID,
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = Instant.now(),
        rejectReason = null,
        item = makeItem("12345678"),
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

    private companion object {
        const val USER_ID = "test-user-id"
    }
}
