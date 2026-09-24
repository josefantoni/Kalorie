package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFoodItemsUseCaseTest {

    // MARK: - Tests

    @Test
    fun search_mergesResultsFromBothNameFields() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf(
            "cz_name_lowercase" to listOf(makeDTO(id = "1", czName = "Tvaroh")),
            "eng_name_lowercase" to listOf(makeDTO(id = "2", czName = "Cottage cheese")),
        )

        val result = sut(query = "tv")

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun search_withSameItemMatchingBothFields_deduplicatesById() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf(
            "cz_name_lowercase" to listOf(makeDTO(id = "1", czName = "Tvaroh")),
            "eng_name_lowercase" to listOf(makeDTO(id = "1", czName = "Tvaroh")),
        )

        val result = sut(query = "tv")

        assertEquals(1, result.size)
    }

    @Test
    fun search_whenEnergyKJMissing_computesItFromMacrosInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf(
            "cz_name_lowercase" to listOf(makeDTOMissingEnergyKJ(fat = 10.0, carbohydrate = 20.0, protein = 5.0)),
        )

        val result = sut(query = "tv")

        // 10g fat + 20g carbohydrate + 5g protein = 370 + 340 + 85 = 795 kJ — a missing source
        // value must not silently read as 0 kJ for a food that clearly has energy.
        assertEquals(795.0, result.first().energyKJ, 0.0)
    }

    @Test
    fun search_withQueryMissingDiacritics_matchesFoldedNameField() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf("cz_name_folded" to listOf(makeDTO(id = "1", czName = "Rohlík")))

        val result = sut(query = "rohlik")

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun search_withResultsFromBothLowercaseAndFoldedFields_deduplicatesById() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf(
            "cz_name_lowercase" to listOf(makeDTO(id = "1", czName = "Rohlík")),
            "cz_name_folded" to listOf(makeDTO(id = "1", czName = "Rohlík")),
        )

        val result = sut(query = "rohlík")

        assertEquals(1, result.size)
    }

    @Test
    fun search_matchesASecondWordByItsToken() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByArrayContainsField = mapOf(
            "cz_name_search_terms" to listOf(makeDTO(id = "1", czName = "Polotučné mléko")),
        )

        val result = sut(query = "mlék")

        assertEquals(listOf("1"), result.map { it.id })
        assertEquals("mlek", dataProvider.arrayContainsValuesByField["cz_name_search_terms"])
        assertEquals("mlek", dataProvider.arrayContainsValuesByField["eng_name_search_terms"])
    }

    @Test
    fun search_withMultiWordQuery_matchesTokenFieldByLastWordOnly() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(query = "polotučné mlék")

        assertEquals("mlek", dataProvider.arrayContainsValuesByField["cz_name_search_terms"])
    }

    @Test
    fun search_withResultFromBothPrefixAndTokenFields_deduplicatesById() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf("cz_name_lowercase" to listOf(makeDTO(id = "1", czName = "Mléko")))
        dataProvider.stubbedByArrayContainsField = mapOf("cz_name_search_terms" to listOf(makeDTO(id = "1", czName = "Mléko")))

        val result = sut(query = "mlék")

        assertEquals(1, result.size)
    }

    @Test
    fun search_mergesInTheFixedFieldOrderWithoutResorting() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByPrefixField = mapOf(
            "cz_name_lowercase" to listOf(makeDTO(id = "cz")),
            "eng_name_lowercase" to listOf(makeDTO(id = "eng")),
            "cz_name_folded" to listOf(makeDTO(id = "czFolded")),
            "eng_name_folded" to listOf(makeDTO(id = "engFolded")),
        )
        dataProvider.stubbedByArrayContainsField = mapOf(
            "cz_name_search_terms" to listOf(makeDTO(id = "czToken")),
            "eng_name_search_terms" to listOf(makeDTO(id = "engToken")),
        )

        val result = sut(query = "tv")

        assertEquals(
            "the concatenation is the user-visible order — a client that merges in another order shows a different list",
            listOf("cz", "eng", "czFolded", "engFolded", "czToken", "engToken"),
            result.map { it.id },
        )
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<SearchFoodItemsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = SearchFoodItemsUseCase(dataProvider = dataProvider)
        return sut to dataProvider
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
        json.remove("energy_kj")
        return FirestoreDataMapper.decode(json, FoodItemDTO.serializer())
    }
}
