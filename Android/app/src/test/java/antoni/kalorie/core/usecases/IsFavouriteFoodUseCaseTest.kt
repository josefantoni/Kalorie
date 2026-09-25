package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IsFavouriteFoodUseCaseTest {

    // MARK: - Tests

    @Test
    fun isFavouriteFood_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun isFavouriteFood_whenDocumentExists_returnsTrue() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = FavouriteFoodDTO(
            item = FoodItemDomain(
                id = "12345",
                kind = FoodItemKind.CATALOGUE,
                czName = "Vejce",
                engName = "Egg",
                weight = 100.0,
                date = Instant.now(),
                energyKJ = 648.0,
                caloriesPerHundredGrams = 155.0,
                fat = 10.0,
                fatSaturated = 3.0,
                fatUnsaturatedFattyAcids = 3.0,
                carbohydrate = 1.0,
                carbohydratePureSugar = 0.0,
                fiber = 0.0,
                protein = 13.0,
                salt = 0.3,
            ),
            favouritedAt = Instant.now(),
        )

        assertTrue(sut("12345"))
    }

    @Test
    fun isFavouriteFood_whenDocumentMissing_returnsFalse() = runTest {
        val (sut, _) = makeSUT()

        assertFalse(sut("12345"))
    }

    @Test
    fun isFavouriteFood_readsTheUserSpecificFavouritesCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("12345")

        assertEquals("users/user-123/favouriteFoods", dataProvider.lastQueriedCollection)
        assertEquals("12345", dataProvider.lastQueriedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<IsFavouriteFoodUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return IsFavouriteFoodUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
