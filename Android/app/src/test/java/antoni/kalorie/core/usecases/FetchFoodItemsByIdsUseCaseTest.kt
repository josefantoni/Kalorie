package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FetchFoodItemsByIdsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetch_queriesTheCatalogueCollection() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf("1"))

        assertEquals("foodItems", dataProvider.queriedCollection)
    }

    @Test
    fun fetch_deduplicatesIdsBeforeQuerying() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf("1", "1", "2"))

        assertEquals(listOf("1", "2"), dataProvider.queriedDocumentIds)
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchFoodItemsByIdsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodItemsByIdsUseCase(dataProvider) to dataProvider
    }
}
