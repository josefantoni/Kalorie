package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.RejectPhotoSuggestionUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModerationPhotoSuggestionViewModelTest {

    // MARK: - Tests

    @Test
    fun onApproveTapped_whileAnApprovalIsInFlight_ignoresFurtherTaps() = runTest {
        val gate = CompletableDeferred<Unit>()
        val approve = ApprovePhotoSuggestionUseCaseSpy(gate)
        val reject = RejectPhotoSuggestionUseCaseSpy()
        var resolvedCount = 0
        val sut = makeSUT(approve, reject) { resolvedCount += 1 }

        val firstTap = launch { sut.onApproveTapped() }
        yield()
        sut.onApproveTapped()
        sut.onRejectTapped()
        gate.complete(Unit)
        firstTap.join()

        assertEquals("a double tap must not upload a second catalogue photo and orphan one of them", 1, approve.callCount)
        assertEquals(0, reject.callCount)
        assertEquals(1, resolvedCount)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        approve: ApprovePhotoSuggestionUseCaseProtocol,
        reject: RejectPhotoSuggestionUseCaseProtocol,
        onResolved: (isApproved: Boolean) -> Unit,
    ) = ModerationPhotoSuggestionViewModel(
        suggestion = PhotoSuggestionDomain(
            itemId = "12345678",
            suggestedBy = "alice",
            photoUrl = "https://example.com/p.jpg",
            suggestedAt = Instant.ofEpochSecond(100),
        ),
        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
        approvePhotoSuggestion = approve,
        rejectPhotoSuggestion = reject,
        onResolved = onResolved,
    )
}

private class ApprovePhotoSuggestionUseCaseSpy(
    private val gate: CompletableDeferred<Unit>,
) : ApprovePhotoSuggestionUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        callCount += 1
        gate.await()
    }
}

private class RejectPhotoSuggestionUseCaseSpy : RejectPhotoSuggestionUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        callCount += 1
    }
}
