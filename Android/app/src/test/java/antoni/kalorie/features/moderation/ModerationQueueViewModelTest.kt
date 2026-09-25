package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationQueueViewModelTest {

    // MARK: - onAppear

    @Test
    fun onAppear_marksSubmissionsWithCollidingBarcodesAsColliding() = runTest {
        val submissionA = makeSubmission(id = "a", barcode = "111")
        val submissionB = makeSubmission(id = "b", barcode = "222")
        val sut = makeSUT(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(stubbedSubmissions = listOf(submissionA, submissionB)),
            fetchFoodItemByBarcode = BarcodeLookupFake(existingBarcodes = mutableSetOf("111")),
        )

        sut.onAppear()

        assertTrue(sut.isColliding(submissionA))
        assertFalse(sut.isColliding(submissionB))
    }

    // MARK: - onSubmissionResolved

    @Test
    fun onSubmissionResolved_removesOnlyThatSubmissionWithoutRefetchingTheQueue() = runTest {
        val submissionA = makeSubmission(id = "a", barcode = "111")
        val submissionB = makeSubmission(id = "b", barcode = "222")
        val fetchPendingSubmissions = FetchPendingSubmissionsUseCaseSpy(listOf(submissionA, submissionB))
        val sut = makeSUT(fetchPendingSubmissions = fetchPendingSubmissions, fetchFoodItemByBarcode = BarcodeLookupFake(mutableSetOf()))
        sut.onAppear()
        assertEquals(1, fetchPendingSubmissions.callCount)

        sut.onSubmissionResolved(id = "a")

        assertEquals(listOf("b"), sut.submissions.value.map { it.id })
        assertEquals(1, fetchPendingSubmissions.callCount)
    }

    @Test
    fun onSubmissionResolved_recomputesCollisionsForRemainingSubmissions() = runTest {
        val submissionA = makeSubmission(id = "a", barcode = "111")
        val submissionB = makeSubmission(id = "b", barcode = "222")
        val barcodeLookup = BarcodeLookupFake(existingBarcodes = mutableSetOf())
        val sut = makeSUT(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(stubbedSubmissions = listOf(submissionA, submissionB)),
            fetchFoodItemByBarcode = barcodeLookup,
        )
        sut.onAppear()
        assertFalse(sut.isColliding(submissionB))

        barcodeLookup.existingBarcodes.add("222")
        sut.onSubmissionResolved(id = "a")

        assertTrue(sut.isColliding(submissionB))
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchPendingSubmissions: FetchPendingSubmissionsUseCaseProtocol = FetchPendingSubmissionsUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
    ): ModerationQueueViewModel = ModerationQueueViewModel(fetchPendingSubmissions, fetchFoodItemByBarcode)

    private fun makeSubmission(id: String, barcode: String): FoodItemSubmissionDomain = FoodItemSubmissionDomain(
        id = id,
        barcode = barcode,
        submittedBy = "some-user",
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = Instant.now(),
        rejectReason = null,
        item = makeItem(id = barcode),
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

private class FetchPendingSubmissionsUseCaseSpy(
    private val stubbedSubmissions: List<FoodItemSubmissionDomain>,
) : FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Properties

    var callCount = 0

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemSubmissionDomain> {
        callCount += 1
        return stubbedSubmissions
    }
}

private class BarcodeLookupFake(
    val existingBarcodes: MutableSet<String>,
) : FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemDomain? {
        if (barcode !in existingBarcodes) return null
        return FoodItemDomain(
            id = barcode,
            kind = FoodItemKind.CATALOGUE,
            czName = "Existing",
            engName = "Existing",
            weight = 100.0,
            date = Instant.now(),
            energyKJ = 100.0,
            caloriesPerHundredGrams = 50.0,
            fat = 1.0,
            fatSaturated = 0.0,
            fatUnsaturatedFattyAcids = 1.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 1.0,
            fiber = 0.0,
            protein = 1.0,
            salt = 0.1,
        )
    }
}
