package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class RejectSubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun reject_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeSubmission(), "Wrong calories")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun reject_withEmptyReason_throwsReasonRequiredAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut(makeSubmission(), "   ")
            fail("Expected reasonRequired error")
        } catch (_: RejectSubmissionError.ReasonRequired) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun reject_withReason_writesRejectedStatusPreservingOtherFields() = runTest {
        val (sut, dataProvider) = makeSUT()
        val submission = makeSubmission()

        sut(submission, "Wrong calories")

        val written = dataProvider.setSavedItem as FoodItemSubmissionDTO
        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.setSavedCollection)
        assertEquals(submission.id, dataProvider.setSavedId)
        assertEquals(FoodItemSubmissionStatus.REJECTED, written.status)
        assertEquals("Wrong calories", written.rejectReason)
        assertEquals(submission.barcode, written.barcode)
        assertEquals(submission.submittedBy, written.submittedBy)
    }

    @Test
    fun reject_whenWriteDeniedAndSubmissionNoLongerExists_throwsAlreadyResolved() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSetError = permissionDenied()
        dataProvider.stubbedServerDocument = null

        try {
            sut(makeSubmission(), "Wrong calories")
            fail("Expected alreadyResolved error")
        } catch (_: RejectSubmissionError.AlreadyResolved) {
        }
    }

    @Test
    fun reject_whenWriteDeniedButSubmissionStillQueuedUnchanged_rethrowsOriginalError() = runTest {
        val (sut, dataProvider) = makeSUT()
        val submission = makeSubmission()
        val deniedError = permissionDenied()
        dataProvider.stubbedSetError = deniedError
        dataProvider.stubbedServerDocument = makeDTO(submission)

        try {
            sut(submission, "Wrong calories")
            fail("Expected the original permissionDenied error")
        } catch (_: RejectSubmissionError.AlreadyResolved) {
            fail("Should not report alreadyResolved while the submission is still queued")
        } catch (error: FirebaseFirestoreException) {
            assertSame(deniedError, error)
        }
    }

    @Test
    fun reject_whenWriteDeniedAndSubmissionWasResubmittedSinceReview_throwsChangedSinceReview() = runTest {
        val (sut, dataProvider) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedSetError = permissionDenied()
        dataProvider.stubbedServerDocument = makeDTO(submission, submittedAt = submission.submittedAt.plusSeconds(60))

        try {
            sut(submission, "Wrong calories")
            fail("Expected changedSinceReview error")
        } catch (_: RejectSubmissionError.ChangedSinceReview) {
        }
    }

    @Test
    fun reject_whenWriteDeniedAndReReadFails_rethrowsOriginalWriteError() = runTest {
        val (sut, dataProvider) = makeSUT()
        val deniedError = permissionDenied()
        dataProvider.stubbedSetError = deniedError
        dataProvider.stubbedServerReadError = permissionDenied()

        try {
            sut(makeSubmission(), "Wrong calories")
            fail("Expected the original write error")
        } catch (_: RejectSubmissionError.AlreadyResolved) {
            fail("Should not report alreadyResolved when the re-read itself fails")
        } catch (error: FirebaseFirestoreException) {
            assertSame(deniedError, error)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Pair<RejectSubmissionUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return RejectSubmissionUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun permissionDenied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    private fun makeSubmission(id: String = "sub-1", barcode: String = "12345678"): FoodItemSubmissionDomain = FoodItemSubmissionDomain(
        id = id,
        barcode = barcode,
        submittedBy = "some-user",
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = Instant.now(),
        rejectReason = null,
        item = FoodItemDomain(
            id = barcode,
            kind = FoodItemKind.CATALOGUE,
            czName = "Tvaroh",
            engName = "",
            weight = 200.0,
            date = Instant.now(),
            energyKJ = 335.0,
            caloriesPerHundredGrams = 80.0,
            fat = 0.5,
            fatSaturated = 0.3,
            fatUnsaturatedFattyAcids = 0.2,
            carbohydrate = 4.0,
            carbohydratePureSugar = 3.0,
            fiber = 0.0,
            protein = 13.0,
            salt = 0.1,
        ),
    )

    private fun makeDTO(submission: FoodItemSubmissionDomain, submittedAt: Instant = submission.submittedAt): FoodItemSubmissionDTO = FoodItemSubmissionDTO(
        id = submission.id,
        barcode = submission.barcode,
        submittedBy = submission.submittedBy,
        status = submission.status,
        submittedAt = submittedAt,
        rejectReason = submission.rejectReason,
        item = submission.item,
    )
}
