package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchMyFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMyFoodItemReport_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345678")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchMyFoodItemReport_readsByTheComposedBarcodeAndUserIdKey() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")

        sut("12345678")

        assertEquals("12345678_user-42", dataProvider.lastQueriedId)
        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.lastQueriedCollection)
    }

    @Test
    fun fetchMyFoodItemReport_whenNoDocument_returnsNull() = runTest {
        val (sut, _) = makeSUT()

        assertNull(sut("12345678"))
    }

    @Test
    fun fetchMyFoodItemReport_whenDocumentExists_returnsIt() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")
        dataProvider.stubbedDocument = FoodItemReportDTO(
            barcode = "12345678",
            reportedBy = "user-42",
            reason = "wrong fat",
            reportedAt = Instant.now(),
        )

        assertEquals("wrong fat", sut("12345678")?.reason)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchMyFoodItemReportUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchMyFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
