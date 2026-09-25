package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchFavouriteFoodsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchFavouriteFoods_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchFavouriteFoods_queriesUserSpecificCollectionOrderedByFavouritedAtDescending() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut()

        assertEquals("users/user-123/favouriteFoods", dataProvider.queriedCollection)
        assertEquals("favourited_at", dataProvider.queriedOrderByField)
        assertEquals(true, dataProvider.queriedDescending)
        assertEquals(50, dataProvider.queriedLimit)
    }

    @Test
    fun fetchFavouriteFoods_mapsStubbedDTOsToDomains() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(makeDTO(id = "12345", czName = "Tvaroh"))

        val result = sut()

        assertEquals(listOf("12345"), result.map { it.id })
        assertEquals(listOf("Tvaroh"), result.map { it.czName })
    }

    @Test
    fun fetchFavouriteFoods_whenFatSaturatedAndFiberMissingFromStoredDocument_stayNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()
        val json = FirestoreDataMapper.encode(makeDTO(id = "12345", czName = "Tvaroh"), FavouriteFoodDTO.serializer())
            .toMutableMap()
            .apply {
                remove("fat_saturated")
                remove("fiber")
            }
        dataProvider.stubbedDocuments = listOf(FirestoreDataMapper.decode(json, FavouriteFoodDTO.serializer()))

        val result = sut()

        assertNull(result.first().fatSaturated)
        assertNull(result.first().fiber)
    }

    @Test
    fun fetchFavouriteFoods_roundTripsMillilitreMeasure() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(makeDTO(id = "12345", czName = "Mléko", measure = FoodMeasure.MILLILITRES))

        val result = sut()

        assertEquals(FoodMeasure.MILLILITRES, result.first().measure)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchFavouriteFoodsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFavouriteFoodsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeDTO(id: String, czName: String, measure: FoodMeasure = FoodMeasure.GRAMS): FavouriteFoodDTO = FavouriteFoodDTO(
        item = FoodItemDomain(
            id = id,
            kind = FoodItemKind.CATALOGUE,
            czName = czName,
            engName = "",
            weight = 100.0,
            date = Instant.now(),
            energyKJ = 0.0,
            caloriesPerHundredGrams = 100.0,
            fat = 0.0,
            fatSaturated = 0.0,
            fatUnsaturatedFattyAcids = 0.0,
            carbohydrate = 0.0,
            carbohydratePureSugar = 0.0,
            fiber = 0.0,
            protein = 0.0,
            salt = 0.0,
            measure = measure,
        ),
        favouritedAt = Instant.now(),
    )
}
