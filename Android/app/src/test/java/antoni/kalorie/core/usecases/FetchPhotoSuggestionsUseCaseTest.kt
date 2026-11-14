package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeSuggestionDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchPhotoSuggestionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetch_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetch_loadsOnlyPendingSuggestions_newestFirst() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        val expected = TicketDataProviderFake.OrderedQuery(
            Constants.Firestore.PHOTO_SUGGESTIONS,
            listOf("status" to "pending"),
            isHidden = null,
            orderField = "suggested_at",
            descending = true,
        )
        assertEquals(listOf(expected), dataProvider.orderedQueries)
    }

    @Test
    fun fetch_returnsEveryPendingSuggestionForAnItem() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = listOf(makeSuggestionDTO(id = "a", itemId = "12345678"), makeSuggestionDTO(id = "b", itemId = "12345678"))

        val suggestions = sut()

        assertEquals("approving one must leave the others pending", listOf("a", "b"), suggestions.map { it.id })
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer"): Pair<FetchPhotoSuggestionsUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return FetchPhotoSuggestionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
