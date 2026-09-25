package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchFoodItemByBarcodeUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchByBarcode_withEmptyBarcode_returnsNil() = runTest {
        val (sut, _) = makeSUT()

        assertNull(sut(""))
    }

    @Test
    fun fetchByBarcode_whenProviderReturnsNil_returnsNil() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = null

        assertNull(sut("8594004428464"))
    }

    @Test
    fun fetchByBarcode_whenProviderReturnsDTO_returnsMappedDomain() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = makeDTO(id = "8594004428464", czName = "Jihočeský tvaroh")

        val result = sut("8594004428464")

        assertEquals("8594004428464", result?.id)
        assertEquals("Jihočeský tvaroh", result?.czName)
        assertEquals(80.0, result?.caloriesPerHundredGrams)
    }

    @Test
    fun fetchByBarcode_queriesCorrectDocumentId() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = makeDTO()

        sut("1234567890")

        assertEquals("1234567890", dataProvider.lastQueriedId)
        assertEquals(Constants.Firestore.FOOD_ITEMS, dataProvider.lastQueriedCollection)
    }

    @Test
    fun fetchByBarcode_whenEnergyKJMissing_computesItFromMacrosInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = makeDTOMissingEnergyKJ(fat = 10.0, carbohydrate = 20.0, protein = 5.0)

        val result = sut("8594004428464")

        assertEquals(
            "10g fat + 20g carbohydrate + 5g protein = 370 + 340 + 85 = 795 kJ — a missing source value must not silently read as 0 kJ",
            795.0,
            result?.energyKJ,
        )
    }

    @Test
    fun fetchByBarcode_whenFatSaturatedAndFiberMissing_stayNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = makeDTO()

        val result = sut("8594004428464")

        assertNull(result?.fatSaturated)
        assertNull(result?.fiber)
    }

    @Test
    fun fetchByBarcodes_withOnlyEmptyBarcodes_returnsEmptyWithoutQuerying() = runTest {
        val (sut, dataProvider) = makeSUT()

        val result = sut(listOf("", ""))

        assertEquals(emptyList<FoodItemDomain>(), result)
        assertNull(dataProvider.queriedCollection)
    }

    @Test
    fun fetchByBarcodes_queriesDeduplicatedNonEmptyBarcodesAndMapsResults() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByDocumentIds = listOf(makeDTO(id = "8594004428464"))

        val result = sut(listOf("8594004428464", "", "8594004428464", "12345678"))

        assertEquals(Constants.Firestore.FOOD_ITEMS, dataProvider.queriedCollection)
        assertEquals(listOf("8594004428464", "12345678"), dataProvider.queriedDocumentIds)
        assertEquals(listOf("8594004428464"), result.map { it.id })
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchFoodItemByBarcodeUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchFoodItemByBarcodeUseCase(dataProvider = dataProvider) to dataProvider
    }

    private fun makeDTO(
        id: String = "8594004428464",
        czName: String = "Tvaroh",
        fat: Double = 0.5,
        carbohydrate: Double = 4.0,
        protein: Double = 13.0,
    ): FoodItemDTO = FoodItemDTO(
        FoodItemDomain(
            id = id,
            kind = FoodItemKind.CATALOGUE,
            czName = czName,
            engName = "Cottage cheese",
            weight = 100.0,
            date = Instant.now(),
            energyKJ = 335.0,
            caloriesPerHundredGrams = 80.0,
            fat = fat,
            fatSaturated = null,
            fatUnsaturatedFattyAcids = 0.2,
            carbohydrate = carbohydrate,
            carbohydratePureSugar = 3.0,
            fiber = null,
            protein = protein,
            salt = 0.1,
        ),
    )

    private fun makeDTOMissingEnergyKJ(fat: Double, carbohydrate: Double, protein: Double): FoodItemDTO {
        val json = FirestoreDataMapper.encode(makeDTO(fat = fat, carbohydrate = carbohydrate, protein = protein), FoodItemDTO.serializer())
            .toMutableMap()
            .apply { remove("energy_kj") }
        return FirestoreDataMapper.decode(json, FoodItemDTO.serializer())
    }
}
