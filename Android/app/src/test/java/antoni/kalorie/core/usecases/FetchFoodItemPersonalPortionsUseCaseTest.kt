package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodPortionDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodItemPersonalPortionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetch_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345678")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetch_readsTheBarcodeDocumentFromTheUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("12345678")

        assertEquals("users/user-123/foodItemPortions", dataProvider.lastQueriedCollection)
        assertEquals("12345678", dataProvider.lastQueriedId)
    }

    @Test
    fun fetch_whenNoDocumentExists_returnsNoPortions() = runTest {
        val (sut, _) = makeSUT()

        assertTrue(sut("12345678").isEmpty())
    }

    @Test
    fun fetch_whenDocumentExists_returnsItsPortionsInStoredOrder() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = FoodItemPersonalPortionsDTO(
            id = "12345678",
            portions = listOf(FoodPortionDTO(name = "1 hrnek", grams = 40.0), FoodPortionDTO(name = "1 balení", grams = 33.0)),
        )

        val portions = sut("12345678")

        assertEquals(listOf(FoodPortionDomain("1 hrnek", 40.0), FoodPortionDomain("1 balení", 33.0)), portions)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchFoodItemPersonalPortionsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodItemPersonalPortionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
