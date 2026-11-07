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

    // MARK: - Helpers

    private fun makeSuggestion(itemId: String, seconds: Long) = PhotoSuggestionDomain(
        itemId = itemId,
        suggestedBy = "alice",
        photoUrl = "https://example.com/p.jpg",
        suggestedAt = Instant.ofEpochSecond(seconds),
    )
}
