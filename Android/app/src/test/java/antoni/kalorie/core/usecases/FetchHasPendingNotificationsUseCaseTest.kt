package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FetchHasPendingNotificationsUseCaseTest {

    // MARK: - Tests

    @Test
    fun whenNothingWaits_returnsFalse() = runTest {
        val (sut, _) = makeSUT()

        assertFalse(sut())
    }

    @Test
    fun whenAnySingleQueueIsNonEmpty_returnsTrue() = runTest {
        val collections = listOf(
            Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            Constants.Firestore.FOOD_ITEM_REPORTS,
            Constants.Firestore.PHOTO_SUGGESTIONS,
        )
        for (collection in collections) {
            val (sut, dataProvider) = makeSUT()
            dataProvider.stubbedCountsByCollection = mapOf(collection to 1)

            assertTrue("$collection alone must light the dot", sut())
        }
    }

    @Test
    fun whenACountFails_returnsFalseInsteadOfShowingAnError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedCountsByCollection = mapOf(Constants.Firestore.PHOTO_SUGGESTIONS to 3)
        dataProvider.stubbedCountError = IOException("offline")

        assertFalse("a stale maintainer claim or a network loss must not surface as an alert on the Dashboard", sut())
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchHasPendingNotificationsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchHasPendingNotificationsUseCase(dataProvider) to dataProvider
    }
}
