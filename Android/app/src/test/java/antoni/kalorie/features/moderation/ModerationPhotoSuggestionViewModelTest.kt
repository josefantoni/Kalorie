package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.makePhotoSuggestionItem
import antoni.kalorie.core.networking.makeSuggestion
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionError
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.RejectPhotoSuggestionUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ModerationPhotoSuggestionViewModelTest {

    // MARK: - Tests

    @Test
    fun onApproveTapped_opensTheSheetPrefilledInTheTicketsLocale_notTheMaintainersOwn() {
        val czech = makeSUT(locale = "cs").sut
        val english = makeSUT(locale = "en").sut

        czech.onApproveTapped()
        english.onApproveTapped()

        assertEquals(TicketStatus.ACCEPTED, czech.responseDraft.value?.status)
        assertEquals("photo-accepted-cs", czech.responseDraft.value?.text)
        assertEquals("photo-accepted-en", english.responseDraft.value?.text)
    }

    @Test
    fun onRejectTapped_opensAnEmptySheet_thatCannotBeSent() {
        val (sut, approve, reject) = makeSUT()

        sut.onRejectTapped()

        assertEquals(TicketStatus.REJECTED, sut.responseDraft.value?.status)
        assertEquals("", sut.responseDraft.value?.text)
        assertEquals("a rejection must be explained to the author", false, sut.responseDraft.value?.isSendable)
        assertTrue("tapping Reject alone changes nothing", approve.calls.isEmpty() && reject.calls.isEmpty())
    }

    @Test
    fun onResponseSendTapped_whenApproving_approvesWithTheEditedResponseAndResolves() = runTest {
        var resolved = 0
        val (sut, approve, reject) = makeSUT { resolved += 1 }
        sut.onApproveTapped()
        sut.onResponseTextChanged("Great photo")

        sut.onResponseSendTapped()

        assertEquals(listOf("Great photo"), approve.calls)
        assertTrue(reject.calls.isEmpty())
        assertEquals(1, resolved)
        assertNull(sut.responseDraft.value)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onResponseSendTapped_whenRejecting_rejectsWithTheResponse() = runTest {
        val (sut, approve, reject) = makeSUT()
        sut.onRejectTapped()
        sut.onResponseTextChanged("Out of focus")

        sut.onResponseSendTapped()

        assertEquals(listOf("Out of focus"), reject.calls)
        assertTrue(approve.calls.isEmpty())
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onResponseSendTapped_withEmptyText_doesNothing() = runTest {
        val (sut, _, reject) = makeSUT()
        sut.onRejectTapped()

        sut.onResponseSendTapped()

        assertTrue(reject.calls.isEmpty())
        assertNotNull(sut.responseDraft.value)
    }

    @Test
    fun onResponseSendTapped_whenTheWriteFails_keepsTheSheetOpenForARetry() = runTest {
        var resolved = 0
        val (sut, approve, _) = makeSUT { resolved += 1 }
        approve.error = IOException("offline")
        sut.onApproveTapped()

        sut.onResponseSendTapped()

        assertNotNull("the maintainer must not have to retype the response", sut.responseDraft.value)
        assertNotNull(sut.responseDraft.value?.errorTitleRes)
        assertEquals(0, resolved)
        assertFalse(sut.shouldDismiss.value)
        assertFalse(sut.isSendingResponse.value)
    }

    @Test
    fun onResponseSendTapped_whileASendIsInFlight_ignoresFurtherTaps() = runTest {
        val gate = CompletableDeferred<Unit>()
        val (sut, approve, _) = makeSUT()
        approve.gate = gate
        sut.onApproveTapped()

        val firstSend = launch { sut.onResponseSendTapped() }
        yield()
        sut.onResponseSendTapped()
        gate.complete(Unit)
        firstSend.join()

        assertEquals("a double tap must not upload a second catalogue photo and orphan one of them", 1, approve.calls.size)
    }

    @Test
    fun onResponseSendTapped_whenTheItemIsGone_closesTheSheetAndBlocksApproval() = runTest {
        val (sut, approve, _) = makeSUT()
        approve.error = ApprovePhotoSuggestionError.ItemMissing
        sut.onApproveTapped()

        sut.onResponseSendTapped()

        assertNull(sut.responseDraft.value)
        assertFalse(sut.canApprove.value)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onApproveTapped_whenAnItemWithAPhotoAlreadyExists_stillOffersApproval() = runTest {
        val item = makePhotoSuggestionItem(photoUrl = "https://example.com/p.jpg")
        val (sut, _, _) = makeSUT(item = FoodItemDTO(item).asDomain())

        sut.onAppear()

        assertTrue("approving replaces the current catalogue photo", sut.canApprove.value)
    }

    // MARK: - Helpers

    private data class SUT(val sut: ModerationPhotoSuggestionViewModel, val approve: ApproveRecorder, val reject: RejectRecorder)

    private fun makeSUT(
        locale: String = "en",
        item: FoodItemDomain? = makePhotoSuggestionItem(),
        onResolved: () -> Unit = {},
    ): SUT {
        val approve = ApproveRecorder()
        val reject = RejectRecorder()
        val sut = ModerationPhotoSuggestionViewModel(
            suggestion = makeSuggestion(itemId = "111", locale = locale),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item),
            approvePhotoSuggestion = approve,
            rejectPhotoSuggestion = reject,
            responseTextProvider = ModerationResponseTextProviderFake(),
            onResolved = onResolved,
        )
        return SUT(sut, approve, reject)
    }
}

private class ApproveRecorder : ApprovePhotoSuggestionUseCaseProtocol {
    var error: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls: MutableList<String> = mutableListOf()

    override suspend fun invoke(suggestion: PhotoSuggestionDomain, response: String) {
        calls.add(response)
        gate?.await()
        error?.let { throw it }
    }
}

private class RejectRecorder : RejectPhotoSuggestionUseCaseProtocol {
    var error: Exception? = null
    val calls: MutableList<String> = mutableListOf()

    override suspend fun invoke(suggestion: PhotoSuggestionDomain, response: String) {
        error?.let { throw it }
        calls.add(response)
    }
}
