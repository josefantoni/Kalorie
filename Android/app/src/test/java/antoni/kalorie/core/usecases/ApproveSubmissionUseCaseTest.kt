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
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ApproveSubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun approve_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _, _) = makeSUT(userId = null)
        val submission = makeSubmission()

        try {
            sut(submission, submission.item)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun approve_withValidSubmission_createsCatalogueItemThenDeletesSubmission() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedServerDocument = makeDTO(submission)

        sut(submission, submission.item)

        assertEquals(submission.item.id, createFoodItem.receivedItem?.id)
        assertEquals(submission.id, dataProvider.deletedId)
        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.deletedFromCollection)
    }

    @Test
    fun approve_whenBarcodeEnteredCatalogueAfterSubmissionWasFiled_refusesAndKeepsSubmission() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedServerDocument = makeDTO(submission)
        createFoodItem.errorToThrow = CreateFoodItemError.ItemAlreadyExists

        try {
            sut(submission, submission.item)
            fail("Expected itemAlreadyExists error")
        } catch (_: CreateFoodItemError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.deletedId)
    }

    @Test
    fun approve_whenSubmissionNoLongerExists_throwsAlreadyResolvedAndNeverCreates() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedServerDocument = null

        try {
            sut(submission, submission.item)
            fail("Expected alreadyResolved error")
        } catch (_: ApproveSubmissionError.AlreadyResolved) {
        }

        assertNull(createFoodItem.receivedItem)
    }

    @Test
    fun approve_whenSubmissionWasResubmittedSinceReview_throwsChangedSinceReviewAndNeverCreates() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedServerDocument = makeDTO(submission, submittedAt = submission.submittedAt.plusSeconds(60))

        try {
            sut(submission, submission.item)
            fail("Expected changedSinceReview error")
        } catch (_: ApproveSubmissionError.ChangedSinceReview) {
        }

        assertNull(createFoodItem.receivedItem)
    }

    @Test
    fun approve_whenSubmissionWasWrittenWithSubMillisecondPrecision_createsTheItemInsteadOfReportingAChange() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val stored = makeDTO(makeSubmission()).copy(submittedAt = 1_758_800_000.123456)
        val submission = stored.asDomain()
        dataProvider.stubbedServerDocument = stored

        sut(submission, submission.item)

        assertEquals(submission.item.id, createFoodItem.receivedItem?.id)
    }

    @Test
    fun approve_whenSubmissionDeleteFailsAfterCreate_swallowsTheErrorSinceTheCatalogueWriteAlreadySucceeded() = runTest {
        val (sut, dataProvider, createFoodItem) = makeSUT()
        val submission = makeSubmission()
        dataProvider.stubbedServerDocument = makeDTO(submission)
        dataProvider.stubbedDeleteError = RuntimeException("delete failed")

        sut(submission, submission.item)

        assertEquals(submission.item.id, createFoodItem.receivedItem?.id)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Triple<ApproveSubmissionUseCase, FirestoreDataProviderFake, RecordingCreateFoodItemUseCase> {
        val dataProvider = FirestoreDataProviderFake()
        val createFoodItem = RecordingCreateFoodItemUseCase()
        val sut = ApproveSubmissionUseCase(dataProvider, AuthProviderFake(userId = userId), createFoodItem)
        return Triple(sut, dataProvider, createFoodItem)
    }

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

private class RecordingCreateFoodItemUseCase : CreateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Exception? = null
    var receivedItem: FoodItemDomain? = null

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemDomain {
        receivedItem = item
        errorToThrow?.let { throw it }
        return item
    }
}
