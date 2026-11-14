package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.makeSuggestion
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ModerationPhotoSuggestionsViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_listsOneRowPerTicketInTheOrderTheQueueReturnsThem() = runTest {
        val sut = makeSUT(
            makeSuggestion(id = "newest", itemId = "33333333"),
            makeSuggestion(id = "middle", itemId = "22222222"),
            makeSuggestion(id = "oldest", itemId = "11111111"),
        )

        sut.onAppear()

        assertEquals("the queue is already newest first; a client sort would hide the order the maintainer asked for", listOf("newest", "middle", "oldest"), sut.rows.value.map { it.id })
    }

    @Test
    fun onSuggestionResolved_dropsOnlyThatTicket_evenWhenAnotherTargetsTheSameItem() = runTest {
        val sut = makeSUT(
            makeSuggestion(id = "alice-ticket", itemId = "11111111"),
            makeSuggestion(id = "bob-ticket", itemId = "11111111"),
            makeSuggestion(id = "other-item", itemId = "22222222"),
        )
        sut.onAppear()

        sut.onSuggestionResolved("alice-ticket")

        assertEquals(
            "approving one suggestion leaves the others pending, so their rows must stay",
            listOf("bob-ticket", "other-item"),
            sut.rows.value.map { it.id },
        )
    }

    // MARK: - Helpers

    private fun makeSUT(vararg suggestions: PhotoSuggestionDomain) = ModerationPhotoSuggestionsViewModel(
        fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = suggestions.toList()),
        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
    )
}
