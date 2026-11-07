package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchPhotoSuggestionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchPhotoSuggestions_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected NotAuthenticated")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchPhotoSuggestions_fetchesTheOldestPageFirst_soTheLongestWaitingSuggestionIsNeverHiddenByTheLimit() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        assertEquals(Constants.Firestore.PHOTO_SUGGESTIONS, dataProvider.queriedCollection)
        assertEquals(Constants.Firestore.Field.SUGGESTED_AT, dataProvider.queriedOrderByField)
        assertEquals(false, dataProvider.queriedDescending)
        assertEquals(Constants.Firestore.REPORTS_PAGE_LIMIT, dataProvider.queriedLimit)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Pair<FetchPhotoSuggestionsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchPhotoSuggestionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
