package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RefreshFavouriteFoodUseCaseTest {

    // MARK: - Tests

    @Test
    fun refresh_whenCatalogueItemWasCorrected_returnsCorrectedItemAndRewritesSnapshot() = runTest {
        val stored = makeItem(calories = 155.0)
        val corrected = makeItem(calories = 140.0)
        val (sut, dataProvider) = makeSUT(
            catalogue = FoodItemDTO(corrected),
            favourite = FavouriteFoodDTO(stored, Instant.ofEpochSecond(1_000)),
        )

        val result = sut(stored)

        assertEquals(corrected, result)
        assertEquals("users/test-user/favouriteFoods", dataProvider.setSavedCollection)
        assertEquals(140.0, dataProvider.savedDTO?.caloriesPerHundredGrams)
    }

    @Test
    fun refresh_keepsOriginalFavouritedAtSoTheOrderingDoesNotJump() = runTest {
        val stored = makeItem(calories = 155.0)
        val (sut, dataProvider) = makeSUT(
            catalogue = FoodItemDTO(makeItem(calories = 140.0)),
            favourite = FavouriteFoodDTO(stored, Instant.ofEpochSecond(1_000)),
        )

        sut(stored)

        assertEquals(1_000.0, dataProvider.savedDTO?.favouritedAt)
    }

    @Test
    fun refresh_whenNothingChanged_doesNotWrite() = runTest {
        val stored = makeItem(calories = 155.0)
        val (sut, dataProvider) = makeSUT(catalogue = FoodItemDTO(stored), favourite = FavouriteFoodDTO(stored, Instant.now()))

        val result = sut(stored)

        assertEquals(stored, result)
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun refresh_whenItemIsNotFromCatalogue_doesNotReadOrWrite() = runTest {
        val external = makeItem(kind = FoodItemKind.EXTERNAL)
        val (sut, dataProvider) = makeSUT(catalogue = FoodItemDTO(makeItem(calories = 1.0)), favourite = null)

        val result = sut(external)

        assertEquals(external, result)
        assertEquals(emptyList<String>(), dataProvider.loadedIdCollections)
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun refresh_whenCatalogueItemNoLongerExists_returnsStoredSnapshot() = runTest {
        val stored = makeItem()
        val (sut, dataProvider) = makeSUT(catalogue = null, favourite = FavouriteFoodDTO(stored, Instant.now()))

        val result = sut(stored)

        assertEquals(stored, result)
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun refresh_whenFavouriteWasRemovedMeanwhile_returnsFreshItemWithoutRecreatingIt() = runTest {
        val corrected = makeItem(calories = 140.0)
        val (sut, dataProvider) = makeSUT(catalogue = FoodItemDTO(corrected), favourite = null)

        val result = sut(makeItem(calories = 155.0))

        assertEquals(corrected, result)
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun refresh_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(catalogue = null, favourite = null, userId = null)

        try {
            sut(makeItem())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private val FirestoreDataProviderFake.savedDTO: FavouriteFoodDTO?
        get() = setSavedItem as? FavouriteFoodDTO

    private fun makeSUT(
        catalogue: FoodItemDTO?,
        favourite: FavouriteFoodDTO?,
        userId: String? = "test-user",
    ): Pair<RefreshFavouriteFoodUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake().apply {
            stubbedDocumentByCollection = mapOf(
                Constants.Firestore.FOOD_ITEMS to catalogue,
                Constants.Firestore.favouriteFoods("test-user") to favourite,
            )
        }
        return RefreshFavouriteFoodUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeItem(kind: FoodItemKind = FoodItemKind.CATALOGUE, calories: Double = 155.0): FoodItemDomain = FoodItemDomain(
        id = "12345",
        kind = kind,
        czName = "Vejce",
        engName = "Egg",
        weight = 100.0,
        date = Instant.ofEpochSecond(500),
        energyKJ = 648.0,
        caloriesPerHundredGrams = calories,
        fat = 10.0,
        fatSaturated = 3.0,
        fatUnsaturatedFattyAcids = 3.0,
        carbohydrate = 1.0,
        carbohydratePureSugar = 0.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.3,
    )
}
