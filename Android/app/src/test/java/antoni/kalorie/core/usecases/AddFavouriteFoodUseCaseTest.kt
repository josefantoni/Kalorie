package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AddFavouriteFoodUseCaseTest {

    // MARK: - Tests

    @Test
    fun addFavouriteFood_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeItem())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun addFavouriteFood_setsDocumentIdToBarcode() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeItem(id = "12345"))

        assertEquals("users/user-123/favouriteFoods", dataProvider.setSavedCollection)
        assertEquals("12345", dataProvider.setSavedId)
        assertEquals("12345", dataProvider.savedDTO?.id)
    }

    @Test
    fun addFavouriteFood_storesTheItemsKindAsFoodItemKind() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(kind = FoodItemKind.EXTERNAL))

        assertEquals(FoodItemKind.EXTERNAL, dataProvider.savedDTO?.foodItemKind)
    }

    @Test
    fun addFavouriteFood_storesTheItemsMeasure() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(measure = FoodMeasure.MILLILITRES))

        assertEquals("millilitres", dataProvider.savedDTO?.measureUnit)
    }

    // MARK: - Helpers

    private val FirestoreDataProviderFake.savedDTO: FavouriteFoodDTO?
        get() = setSavedItem as? FavouriteFoodDTO

    private fun makeSUT(userId: String? = "test-user"): Pair<AddFavouriteFoodUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return AddFavouriteFoodUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeItem(
        id: String = "12345",
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        measure: FoodMeasure = FoodMeasure.GRAMS,
    ): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = kind,
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
        measure = measure,
    )
}
