package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeSuggestionDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FetchMyPhotoSuggestionUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMyPhotoSuggestion_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345678")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchMyPhotoSuggestion_asksOnlyForTheUsersPendingTicketForThatItem() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")

        sut("12345678")

        assertEquals(
            "a resolved ticket must not block a new suggestion, so the query is pending only",
            listOf(
                TicketDataProviderFake.EqualityQuery(
                    Constants.Firestore.PHOTO_SUGGESTIONS,
                    listOf("suggested_by" to "user-42", "item_id" to "12345678", "status" to "pending"),
                    limit = 1,
                ),
            ),
            dataProvider.equalityQueries,
        )
    }

    @Test
    fun fetchMyPhotoSuggestion_withoutAPendingTicket_returnsFalse() = runTest {
        val (sut, _) = makeSUT()

        assertFalse(sut("12345678"))
    }

    @Test
    fun fetchMyPhotoSuggestion_withAPendingTicket_returnsTrue() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = listOf(makeSuggestionDTO())

        assertTrue(sut("12345678"))
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchMyPhotoSuggestionUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return FetchMyPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
