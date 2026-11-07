package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ModerationPhotoSuggestionsViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_listsTheOldestSuggestionFirst_whateverOrderTheFetchReturns() = runTest {
        val newest = makeSuggestion("33333333", 300)
        val oldest = makeSuggestion("11111111", 100)
        val middle = makeSuggestion("22222222", 200)
        val sut = ModerationPhotoSuggestionsViewModel(
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(newest, oldest, middle)),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
        )

        sut.onAppear()

        assertEquals(
            "the longest-waiting suggestion is reviewed first",
            listOf("11111111", "22222222", "33333333"),
            sut.rows.value.map { it.suggestion.itemId },
        )
    }

    @Test
    fun onSuggestionResolved_whenApproved_dropsEverySuggestionForThatItem() = runTest {
        val sut = makeSUT(
            makeSuggestion("11111111", 100, "alice"),
            makeSuggestion("11111111", 200, "bob"),
            makeSuggestion("22222222", 300, "alice"),
        )
        sut.onAppear()

        sut.onSuggestionResolved("11111111_alice", isApproved = true)

        assertEquals(
            "approval deletes every suggestion for the item, so a stale row for it must not stay",
            listOf("22222222"),
            sut.rows.value.map { it.suggestion.itemId },
        )
    }

    @Test
    fun onSuggestionResolved_whenRejected_dropsOnlyThatSuggestion() = runTest {
        val sut = makeSUT(
            makeSuggestion("11111111", 100, "alice"),
            makeSuggestion("11111111", 200, "bob"),
        )
        sut.onAppear()

        sut.onSuggestionResolved("11111111_alice", isApproved = false)

        assertEquals(listOf("11111111_bob"), sut.rows.value.map { it.id })
    }

    // MARK: - Helpers

    private fun makeSUT(vararg suggestions: PhotoSuggestionDomain) = ModerationPhotoSuggestionsViewModel(
        fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = suggestions.toList()),
        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
    )

    private fun makeSuggestion(itemId: String, seconds: Long, suggestedBy: String = "alice") = PhotoSuggestionDomain(
        itemId = itemId,
        suggestedBy = suggestedBy,
        photoUrl = "https://example.com/p.jpg",
        suggestedAt = Instant.ofEpochSecond(seconds),
    )
}
