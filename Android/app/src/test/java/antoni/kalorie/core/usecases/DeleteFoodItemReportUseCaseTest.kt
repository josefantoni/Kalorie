package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DeleteFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun deleteFoodItemReport_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut("12345678", "user-1")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertNull(dataProvider.deletedId)
    }

    @Test
    fun deleteFoodItemReport_deletesByTheComposedBarcodeAndReportedByKey() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "user-1")

        assertEquals("12345678_user-1", dataProvider.deletedId)
        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.deletedFromCollection)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer-user"): Pair<DeleteFoodItemReportUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return DeleteFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
