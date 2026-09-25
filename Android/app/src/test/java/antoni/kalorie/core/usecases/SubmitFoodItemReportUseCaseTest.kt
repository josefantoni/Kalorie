package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemReportError
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SubmitFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun submitFoodItemReport_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut("12345678", "wrong calories")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submitFoodItemReport_withEmptyReason_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "   ")
            fail("Expected reasonRequired error")
        } catch (_: FoodItemReportError.ReasonRequired) {
        }

        assertNull("a report with no text is a downvote the maintainer cannot act on", dataProvider.setSavedId)
    }

    @Test
    fun submitFoodItemReport_withReasonOverLimit_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "a".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH + 1))
            fail("Expected reasonTooLong error")
        } catch (_: FoodItemReportError.ReasonTooLong) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submitFoodItemReport_withReasonOverLimitInUTF16Units_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "\uD83D\uDE00".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH))
            fail("Expected reasonTooLong error")
        } catch (_: FoodItemReportError.ReasonTooLong) {
        }

        assertNull("the rules count UTF-16 units, so a reason within the limit in code points is still refused server-side", dataProvider.setSavedId)
    }

    @Test
    fun submitFoodItemReport_withDiacriticsAtLimit_writes() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "ě".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH))

        assertNotNull("the rules accept this, so a Czech reason must not be cut short", dataProvider.setSavedId)
    }

    @Test
    fun submitFoodItemReport_writesUnderTheComposedBarcodeAndUserIdKey() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")

        sut("12345678", "the fat is wrong")

        assertEquals(
            "the rules verify this exact composition, and a mismatch fails every write with permissionDenied",
            "12345678_user-42",
            dataProvider.setSavedId,
        )
        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.setSavedCollection)
    }

    @Test
    fun submitFoodItemReport_trimsWhitespaceFromReason() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "  the fat is wrong  ")

        assertEquals("the fat is wrong", (dataProvider.setSavedItem as FoodItemReportDTO).reason)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<SubmitFoodItemReportUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return SubmitFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
