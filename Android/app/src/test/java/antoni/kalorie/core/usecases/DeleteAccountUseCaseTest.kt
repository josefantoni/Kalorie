package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderFake
import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class DeleteAccountUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_deletesFavouriteFoodsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.favouriteFoods(USER_ID) to listOf("fav1"))

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
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.myCreatedMeals(USER_ID) to listOf("meal1"))

        fixture.sut()

        assertEquals(listOf("meal1"), fixture.deleted(Constants.Firestore.myCreatedMeals(USER_ID)))
    }

    @Test
    fun invoke_keepsTheMergedAnonymousAccountMarkersSoTheCleanupScriptCanStillFindTheAbandonedAccount() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(
            Constants.Firestore.mergedAnonymousAccounts(USER_ID) to listOf("anon-1"),
        )

        fixture.sut()

        assertEquals(emptyList<String>(), fixture.deleted(Constants.Firestore.mergedAnonymousAccounts(USER_ID)))
    }

    @Test
    fun invoke_deletesBodyWeightsAndSettingsAsPrivacyObligation_beforeTheProfile() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(
            Constants.Firestore.bodyWeights(USER_ID) to listOf("2026-10-27", "2026-10-26"),
        )

        fixture.sut()

        assertEquals(
            "body weight is health data — Firestore does not cascade, so it is wiped in one batch rather than a round trip per day",
            listOf("2026-10-27", "2026-10-26"),
            fixture.dataProvider.batchDeletedIdsByCollection[Constants.Firestore.bodyWeights(USER_ID)],
        )
        assertNull(fixture.dataProvider.deletedIdsByCollection[Constants.Firestore.bodyWeights(USER_ID)])
        assertEquals(listOf("app"), fixture.deleted(Constants.Firestore.settings(USER_ID)))
        val order = fixture.dataProvider.deletionCollectionOrder
        assertTrue(order.indexOf(Constants.Firestore.settings(USER_ID)) < order.indexOf(Constants.Firestore.USERS))
    }

    @Test
    fun invoke_deletesFoodItemPortionsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodItemPortions(USER_ID) to listOf("12345678"))

        fixture.sut()

        assertEquals(listOf("12345678"), fixture.deleted(Constants.Firestore.foodItemPortions(USER_ID)))
    }

    @Test
    fun invoke_deletesOwnSubmissionsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.FOOD_ITEM_SUBMISSIONS to listOf("sub1"))

        fixture.sut()

        assertEquals(
            "a pending or rejected submission is the user's own data and must not survive account deletion",
            listOf("sub1"),
            fixture.deleted(Constants.Firestore.FOOD_ITEM_SUBMISSIONS),
        )
    }

    @Test
    fun invoke_deletesTheUsersSubmissionPhotosButNotCatalogueOrOtherUsersPhotos() = runTest {
        val storage = StorageDataProviderFake()
        val own = storage.uploadAsync(byteArrayOf(1), "submissionPhotos/$USER_ID/a.jpg", "image/jpeg")
        val orphan = storage.uploadAsync(byteArrayOf(2), "submissionPhotos/$USER_ID/orphan.jpg", "image/jpeg")
        val someoneElse = storage.uploadAsync(byteArrayOf(3), "submissionPhotos/other-user/b.jpg", "image/jpeg")
        val catalogue = storage.uploadAsync(byteArrayOf(4), "catalogPhotos/12345678/c.jpg", "image/jpeg")
        val fixture = makeSUT(storage = storage)

        fixture.sut()

        assertNull(storage.files[own])
        assertNull("orphans from failed writes are not reachable through the deleted submissions, only through the folder", storage.files[orphan])
        assertNotNull(storage.files[someoneElse])
        assertNotNull("approved items are shared catalogue content and outlive the account", storage.files[catalogue])
    }

    @Test
    fun invoke_whenListingPhotosFails_doesNotDeleteTheAuthUser() = runTest {
        val storage = StorageDataProviderFake()
        storage.listError = RuntimeException("offline")
        val fixture = makeSUT(storage = storage)

        try {
            fixture.sut()
        } catch (_: RuntimeException) {
        }

        assertTrue("the photos hold the user's content; the account must survive so the deletion can be retried", fixture.snapshots.isEmpty())
    }

    @Test
    fun invoke_deletesOwnReportsAsPrivacyObligation() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.FOOD_ITEM_REPORTS to listOf("12345678_$USER_ID"))

        fixture.sut()

        assertEquals(
            "a report is the user's own data and must not survive account deletion",
            listOf("12345678_$USER_ID"),
            fixture.deleted(Constants.Firestore.FOOD_ITEM_REPORTS),
        )
    }

    @Test
    fun invoke_whenADocumentDoesNotMatchItsDTO_stillDeletesItAndTheAccount() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDecodingError = SerializationException("stubbed")
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("broken"))

        fixture.sut()

        assertEquals(
            "a document that no longer decodes is still the user's data and must not block the wipe",
            listOf("broken"),
            fixture.deletedBeforeAccountDeletion[Constants.Firestore.foodConsumed(USER_ID)],
        )
        assertEquals(1, fixture.authCommandProvider.deleteCallCount)
    }

    @Test
    fun invoke_deletesFoodFrequencyDocument() = runTest {
        val fixture = makeSUT()

        fixture.sut()

        assertEquals(
            "the per-user frequency counts are private data and must not outlive the account",
            listOf("foodFrequency"),
            fixture.deleted(Constants.Firestore.stats(USER_ID)),
        )
    }

    @Test
    fun invoke_deletesFirestoreDataBeforeDeletingAuthAccount() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(
            Constants.Firestore.mealTypes(USER_ID) to listOf("0"),
            Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"),
        )

        fixture.sut()

        assertEquals(1, fixture.authCommandProvider.deleteCallCount)
        assertEquals(listOf("0"), fixture.deletedBeforeAccountDeletion[Constants.Firestore.mealTypes(USER_ID)])
        assertEquals(listOf("f1"), fixture.deletedBeforeAccountDeletion[Constants.Firestore.foodConsumed(USER_ID)])
    }

    @Test
    fun invoke_deletesTheUserProfileDocumentLast() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"))

        fixture.sut()

        assertEquals(listOf(USER_ID), fixture.deleted(Constants.Firestore.USERS))
        assertEquals(
            "a failure part-way must leave the profile in place so the account is not half-erased",
            listOf(
                Constants.Firestore.foodConsumed(USER_ID),
                Constants.Firestore.settings(USER_ID),
                Constants.Firestore.stats(USER_ID),
                Constants.Firestore.USERS,
            ),
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
    fun invoke_whenAuthenticationIsStale_throwsBeforeDeletingAnyData() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(userId = USER_ID, authenticationDate = Instant.now().minusSeconds(10 * 60)))
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"))
        fixture.snapshotStore.stubbedSnapshot = makeSnapshot()

        try {
            fixture.sut()
            fail("Expected DeleteAccountError.RequiresRecentLogin to be thrown")
        } catch (error: DeleteAccountError.RequiresRecentLogin) {
            assertNotNull("a rejected attempt must leave everything intact so the user can simply retry", fixture.snapshotStore.stubbedSnapshot)
            assertTrue(
                "a stale session must be rejected before any data is deleted, otherwise the account survives but its history does not",
                fixture.dataProvider.allDeletedIdsByCollection.isEmpty(),
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
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.mealTypes(USER_ID) to listOf("0"))

        fixture.sut(skipDataWipe = true)

        assertTrue("a retry that already wiped the data must not re-read and re-delete it", fixture.dataProvider.allDeletedIdsByCollection.isEmpty())
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
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"))
        fixture.snapshotStore.deleteError = IllegalStateException("disk")

        try {
            fixture.sut()
            fail("Expected the snapshot error to be thrown")
        } catch (_: IllegalStateException) {
            assertTrue(
                "the account must survive intact when the snapshot cannot be cleared, so the user can retry",
                fixture.dataProvider.allDeletedIdsByCollection.isEmpty(),
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

    @Test
    fun invoke_whenAccountHasApple_revokesTheAppleTokenBeforeDeletingTheUser() = runTest {
        val order = mutableListOf<String>()
        val fixture = makeSUT(
            authProvider = AuthProviderFake(userId = USER_ID, linkedProviderKinds = setOf(AuthProviderKind.APPLE)),
            appleSignInProvider = AppleSignInProviderFake(result = AppleAuthResult(displayName = null, email = null, accessToken = "apple-token")),
        )
        fixture.authCommandProvider.onRevoke = { order += "revoke" }
        fixture.authCommandProvider.onDelete = { order += "delete" }

        fixture.sut()

        assertEquals(listOf("apple-token"), fixture.authCommandProvider.revokedAccessTokens)
        assertEquals("Apple requires the token to be revoked as part of account deletion, and revoking after the user is gone is impossible", listOf("revoke", "delete"), order)
    }

    @Test
    fun invoke_whenAccountHasNoApple_doesNotAskAppleForAuthorizationOrRevoke() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(userId = USER_ID, linkedProviderKinds = setOf(AuthProviderKind.GOOGLE)))

        fixture.sut()

        assertEquals(0, fixture.appleSignInProvider.reauthenticateCallCount)
        assertTrue(fixture.authCommandProvider.revokedAccessTokens.isEmpty())
    }

    @Test
    fun invoke_whenRevocationFails_stillDeletesTheUser() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(userId = USER_ID, linkedProviderKinds = setOf(AuthProviderKind.APPLE)))
        fixture.authCommandProvider.revokeError = RuntimeException("offline")

        fixture.sut()

        assertEquals("a failed revocation is only logged: the user asked to delete the account and must not be stuck with it", 1, fixture.authCommandProvider.deleteCallCount)
    }

    @Test
    fun invoke_whenAppleAuthorizationFails_deletesNothing() = runTest {
        val fixture = makeSUT(
            authProvider = AuthProviderFake(userId = USER_ID, linkedProviderKinds = setOf(AuthProviderKind.APPLE)),
            appleSignInProvider = AppleSignInProviderFake(reauthenticateError = RuntimeException("cancelled")),
        )
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"))

        try {
            fixture.sut()
            fail("Expected the failure to be thrown")
        } catch (_: RuntimeException) {
        }

        assertTrue("cancelling the Apple prompt must leave the account and its data intact so the user can retry", fixture.dataProvider.allDeletedIdsByCollection.isEmpty())
        assertEquals(0, fixture.authCommandProvider.deleteCallCount)
    }

    @Test
    fun invoke_whenAccountHasApple_andLoginIsStale_reauthenticatesWithAppleInsteadOfAskingForAnotherLogin() = runTest {
        val fixture = makeSUT(
            authProvider = AuthProviderFake(
                userId = USER_ID,
                linkedProviderKinds = setOf(AuthProviderKind.APPLE),
                authenticationDate = Instant.now().minusSeconds(10 * 60),
            ),
            appleSignInProvider = AppleSignInProviderFake(result = AppleAuthResult(displayName = null, email = null, accessToken = "apple-token")),
        )

        fixture.sut()

        assertEquals("the Apple prompt already proves a fresh login, so a stale one must not send the user through a second prompt", 1, fixture.appleSignInProvider.reauthenticateCallCount)
        assertEquals(1, fixture.authCommandProvider.deleteCallCount)
    }

    @Test
    fun invoke_whenAppleIdentityDoesNotMatchTheAccount_deletesAndRevokesNothing() = runTest {
        val fixture = makeSUT(
            authProvider = AuthProviderFake(userId = USER_ID, linkedProviderKinds = setOf(AuthProviderKind.APPLE)),
            appleSignInProvider = AppleSignInProviderFake(reauthenticateError = FirebaseAuthException("ERROR_USER_MISMATCH", "mismatch")),
        )
        fixture.dataProvider.stubbedDocumentIdsByCollection = mapOf(Constants.Firestore.foodConsumed(USER_ID) to listOf("f1"))

        try {
            fixture.sut()
            fail("Expected ReauthenticateError.WrongAccount to be thrown")
        } catch (_: ReauthenticateError.WrongAccount) {
        }

        assertTrue("an access token of another Apple identity would revoke the wrong grant, so nothing may be revoked or deleted", fixture.dataProvider.allDeletedIdsByCollection.isEmpty())
        assertTrue(fixture.authCommandProvider.revokedAccessTokens.isEmpty())
        assertEquals(0, fixture.authCommandProvider.deleteCallCount)
    }

    private class Fixture(
        val dataProvider: FirestoreDataProviderFake,
        val authCommandProvider: AuthCommandProviderFake,
        val snapshotStore: PendingMergeSnapshotStoreFake,
        val sut: DeleteAccountUseCase,
        val appleSignInProvider: AppleSignInProviderFake,
        val snapshots: MutableList<Map<String, List<String>>>,
    ) {
        val deletedBeforeAccountDeletion: Map<String, List<String>>
            get() = snapshots.firstOrNull().orEmpty()

        fun deleted(collection: String): List<String> = dataProvider.allDeletedIdsByCollection[collection].orEmpty()
    }

    private fun makeSUT(
        authProvider: AuthProviderFake = AuthProviderFake(userId = USER_ID),
        storage: StorageDataProviderFake = StorageDataProviderFake(),
        appleSignInProvider: AppleSignInProviderFake = AppleSignInProviderFake(),
    ): Fixture {
        val dataProvider = FirestoreDataProviderFake()
        val snapshots = mutableListOf<Map<String, List<String>>>()
        val authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.onDelete = { snapshots += dataProvider.allDeletedIdsByCollection }
        val snapshotStore = PendingMergeSnapshotStoreFake()
        val sut = DeleteAccountUseCase(
            dataProvider = dataProvider,
            authProvider = authProvider,
            authCommandProvider = authCommandProvider,
            snapshotStore = snapshotStore,
            storageProvider = storage,
            appleSignInProvider = appleSignInProvider,
        )
        return Fixture(dataProvider, authCommandProvider, snapshotStore, sut, appleSignInProvider, snapshots)
    }

    private fun makeSnapshot() = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = emptyList())

    private companion object {
        const val USER_ID = "test-user-id"
    }
}
