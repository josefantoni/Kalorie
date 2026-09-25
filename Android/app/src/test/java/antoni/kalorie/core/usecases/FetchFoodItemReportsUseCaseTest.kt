package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodItemReportsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchFoodItemReports_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchFoodItemReports_queriesWholeCollectionOrderedByReportedAtDescending() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.queriedCollection)
        assertEquals("reported_at", dataProvider.queriedOrderByField)
        assertEquals(true, dataProvider.queriedDescending)
        assertEquals(Constants.Firestore.REPORTS_PAGE_LIMIT, dataProvider.queriedLimit)
    }

    @Test
    fun fetchFoodItemReports_mapsDTOsToDomain() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            FoodItemReportDTO(barcode = "12345678", reportedBy = "user-1", reason = "wrong fat", reportedAt = Instant.now()),
        )

        val result = sut()

        assertEquals(listOf("12345678"), result.map { it.barcode })
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Pair<FetchFoodItemReportsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodItemReportsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
