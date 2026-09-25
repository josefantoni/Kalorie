package antoni.kalorie.features.moderation

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.usecases.ApproveSubmissionError
import antoni.kalorie.core.usecases.ApproveSubmissionUseCaseFake
import antoni.kalorie.core.usecases.ApproveSubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.CreateFoodItemError
import antoni.kalorie.core.usecases.RejectSubmissionError
import antoni.kalorie.core.usecases.RejectSubmissionUseCaseFake
import antoni.kalorie.core.usecases.RejectSubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationReviewViewModelTest {

    // MARK: - onApproveTapped

    @Test
    fun onApproveTapped_preservesTheOriginalSubmissionsDate() = runTest {
        val originalDate = Instant.ofEpochSecond(1_700_000_000)
        val approveSubmission = ApproveSubmissionUseCaseSpy()
        val sut = makeSUT(submission = makeSubmission(date = originalDate), approveSubmission = approveSubmission)

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onApproveTapped()

        assertEquals(originalDate, approveSubmission.receivedItem?.date)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onApproveTapped_preservesTheOriginalEnglishName() = runTest {
        val approveSubmission = ApproveSubmissionUseCaseSpy()
        val sut = makeSUT(submission = makeSubmission(engName = "Cottage cheese"), approveSubmission = approveSubmission)

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onApproveTapped()

        assertEquals("Cottage cheese", approveSubmission.receivedItem?.engName)
    }

    // MARK: - onApproveTapped error handling

    @Test
    fun onApproveTapped_whenValidationFails_showsFieldSpecificMessageAndDoesNotDismiss() = runTest {
        val approveSubmission = ApproveSubmissionUseCaseSpy(errorToThrow = CreateFoodItemError.InvalidWeight)
        val sut = makeSUT(approveSubmission = approveSubmission)

        sut.onApproveTapped()

        assertEquals(R.string.addFood_error_invalidWeight, sut.alertItem.value?.titleRes)
        assertFalse(sut.shouldDismiss.value)
    }

    @Test
    fun onApproveTapped_whenBarcodeCollides_showsAlreadyExistsMessage() = runTest {
        val approveSubmission = ApproveSubmissionUseCaseSpy(errorToThrow = CreateFoodItemError.ItemAlreadyExists)
        val sut = makeSUT(approveSubmission = approveSubmission)

        sut.onApproveTapped()

        assertEquals(R.string.moderation_error_alreadyExists, sut.alertItem.value?.titleRes)
        assertFalse(sut.shouldDismiss.value)
    }

    @Test
    fun onApproveTapped_whenSubmissionAlreadyResolved_showsMessageAndDismisses() = runTest {
        val approveSubmission = ApproveSubmissionUseCaseSpy(errorToThrow = ApproveSubmissionError.AlreadyResolved)
        val sut = makeSUT(approveSubmission = approveSubmission)

        sut.onApproveTapped()

        assertEquals(R.string.moderation_error_alreadyResolved, sut.alertItem.value?.titleRes)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onApproveTapped_whenSubmissionChangedSinceReview_showsMessageAndDismisses() = runTest {
        val approveSubmission = ApproveSubmissionUseCaseSpy(errorToThrow = ApproveSubmissionError.ChangedSinceReview)
        val sut = makeSUT(approveSubmission = approveSubmission)

        sut.onApproveTapped()

        assertEquals(R.string.moderation_error_changedSinceReview, sut.alertItem.value?.titleRes)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - onRejectConfirmed error handling

    @Test
    fun onRejectConfirmed_whenSubmissionChangedSinceReview_showsMessageAndDismisses() = runTest {
        val rejectSubmission = RejectSubmissionUseCaseFake(errorToThrow = RejectSubmissionError.ChangedSinceReview)
        val sut = makeSUT(rejectSubmission = rejectSubmission)
        sut.rejectReason.value = "Wrong calories"

        sut.onRejectConfirmed()

        assertEquals(R.string.moderation_error_changedSinceReview, sut.alertItem.value?.titleRes)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - onAppear / similar catalogue items

    @Test
    fun onAppear_withBarcode_doesNotSearchForSimilarItems() = runTest {
        val searchFoodItems = SearchFoodItemsUseCaseSpy()
        val sut = makeSUT(submission = makeSubmission(barcode = "12345678"), searchFoodItems = searchFoodItems)

        sut.onAppear()

        assertNull(searchFoodItems.receivedQuery)
        assertFalse(sut.showsSimilarCatalogueItemsSection)
    }

    @Test
    fun onAppear_withoutBarcode_searchesByNameAndPublishesResults() = runTest {
        val match = makeItem(id = "existing", czName = "Tvaroh")
        val searchFoodItems = SearchFoodItemsUseCaseSpy(stubbedItems = listOf(match))
        val sut = makeSUT(submission = makeSubmission(barcode = null), searchFoodItems = searchFoodItems)

        sut.onAppear()

        assertEquals("Tvaroh", searchFoodItems.receivedQuery)
        assertEquals(listOf("existing"), sut.similarCatalogueItems.value.map { it.id })
        assertTrue(sut.showsSimilarCatalogueItemsSection)
        assertTrue(sut.isSimilarCatalogueItemsSectionAvailable.value)
    }

    @Test
    fun onAppear_whenSearchFails_marksSectionUnavailableButDoesNotBlockApproval() = runTest {
        val searchFoodItems = SearchFoodItemsUseCaseSpy(shouldThrow = true)
        val approveSubmission = ApproveSubmissionUseCaseSpy()
        val sut = makeSUT(submission = makeSubmission(barcode = null), approveSubmission = approveSubmission, searchFoodItems = searchFoodItems)

        sut.onAppear()
        assertFalse(sut.isSimilarCatalogueItemsSectionAvailable.value)

        sut.onApproveTapped()
        assertNotNull(approveSubmission.receivedItem)
    }

    // MARK: - Helpers

    private fun makeSUT(
        submission: FoodItemSubmissionDomain = makeSubmission(),
        approveSubmission: ApproveSubmissionUseCaseProtocol = ApproveSubmissionUseCaseFake(),
        rejectSubmission: RejectSubmissionUseCaseProtocol = RejectSubmissionUseCaseFake(),
        searchFoodItems: SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
    ): ModerationReviewViewModel = ModerationReviewViewModel(
        submission = submission,
        approveSubmission = approveSubmission,
        rejectSubmission = rejectSubmission,
        searchFoodItems = searchFoodItems,
        onResolved = {},
    )

    private fun makeSubmission(id: String = "sub-1", barcode: String? = "12345678", date: Instant = Instant.now(), engName: String = "Cottage cheese"): FoodItemSubmissionDomain = FoodItemSubmissionDomain(
        id = id,
        barcode = barcode,
        submittedBy = "some-user",
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = Instant.now(),
        rejectReason = null,
        item = makeItem(id = barcode ?: "9A5E1B2C-8D3F-4A6E-9C1D-7B2A4E5F6C8D", date = date).copy(engName = engName),
    )

    private fun makeItem(id: String = "12345678", czName: String = "Tvaroh", date: Instant = Instant.now()): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = czName,
        engName = "Cottage cheese",
        weight = 200.0,
        date = date,
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
    )
}

private class ApproveSubmissionUseCaseSpy(
    private val errorToThrow: Exception? = null,
) : ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    var receivedItem: FoodItemDomain? = null

    // MARK: - Functions

    override suspend fun invoke(submission: FoodItemSubmissionDomain, item: FoodItemDomain) {
        receivedItem = item
        errorToThrow?.let { throw it }
    }
}

private class SearchFoodItemsUseCaseSpy(
    private val stubbedItems: List<FoodItemDomain> = emptyList(),
    private val shouldThrow: Boolean = false,
) : SearchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    var receivedQuery: String? = null

    // MARK: - Functions

    override suspend fun invoke(query: String): List<FoodItemDomain> {
        receivedQuery = query
        if (shouldThrow) throw RuntimeException("search failed")
        return stubbedItems
    }
}
