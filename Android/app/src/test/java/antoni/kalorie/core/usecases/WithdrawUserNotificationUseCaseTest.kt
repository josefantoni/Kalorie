package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeNotification
import antoni.kalorie.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

class WithdrawUserNotificationUseCaseTest {

    // MARK: - Tests

    @Test
    fun withdraw_aPendingReport_deletesItsDocument() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        sut(makeNotification(id = "report-3"))

        assertEquals(listOf("report-3" to Constants.Firestore.FOOD_ITEM_REPORTS), dataProvider.deletes)
    }

    @Test
    fun withdraw_aPendingPhoto_deletesTheFileBeforeTheDocument() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val notification = makeNotification(kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION, id = "photo-3")
        val url = requireNotNull(notification.photoUrl)
        storage.files[url] = byteArrayOf(1)

        sut(notification)

        assertEquals("the path comes from the stored photo_url — an anonymised ticket has no uid to build it from", listOf(url), storage.deletedUrls)
        assertEquals(listOf("photo-3" to Constants.Firestore.PHOTO_SUGGESTIONS), dataProvider.deletes)
    }

    @Test
    fun withdraw_aPhotoWhoseFileIsAlreadyGone_stillDeletesTheDocument() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        storage.deleteError = StorageException.fromExceptionAndHttpCode(null, 404)

        sut(makeNotification(kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION, id = "photo-3"))

        assertEquals(listOf("photo-3"), dataProvider.deletes.map { it.first })
    }

    @Test
    fun withdraw_whenTheFileDeleteFails_keepsTheDocumentForARetry() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        storage.deleteError = IOException("offline")

        try {
            sut(makeNotification(kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION))
            fail("the failure must reach the caller")
        } catch (_: IOException) {
        }

        assertTrue("deleting the document first would orphan the file", dataProvider.deletes.isEmpty())
    }

    @Test
    fun withdraw_aResolvedTicket_isRefusedWithoutDeleting() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        try {
            sut(makeNotification(status = TicketStatus.ACCEPTED, resolvedAt = Instant.now()))
            fail("a resolved ticket is the record of what the maintainer did")
        } catch (_: WithdrawUserNotificationError.AlreadyResolved) {
        }

        assertTrue(dataProvider.deletes.isEmpty())
    }

    @Test
    fun withdraw_whenTheMaintainerResolvedItMeanwhile_throwsAlreadyResolved() = runTest {
        val (sut, dataProvider, _) = makeSUT()
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)
        dataProvider.failingError = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        try {
            sut(makeNotification())
            fail("PERMISSION_DENIED on a pending delete means the rules saw a resolved ticket")
        } catch (_: WithdrawUserNotificationError.AlreadyResolved) {
        }
    }

    @Test
    fun withdraw_whenTheDeleteFailsForAnotherReason_rethrowsIt() = runTest {
        val (sut, dataProvider, _) = makeSUT()
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)
        dataProvider.failingError = FirebaseFirestoreException("down", FirebaseFirestoreException.Code.UNAVAILABLE)

        try {
            sut(makeNotification())
            fail("Expected the original error")
        } catch (error: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.UNAVAILABLE, error.code)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Triple<WithdrawUserNotificationUseCase, TicketDataProviderFake, StorageDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = WithdrawUserNotificationUseCase(dataProvider, AuthProviderFake(userId = userId), storage)
        return Triple(sut, dataProvider, storage)
    }
}
