package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.networking.makeReport
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ModerationReportsViewModelTest {

    @Test
    fun onAppear_showsOneRowPerTicket_evenForTheSameItem() = runTest {
        val sut = makeSUT(
            reports = listOf(
                makeReport(id = "a", barcode = "111"),
                makeReport(id = "b", barcode = "111"),
                makeReport(id = "c", barcode = "222"),
            ),
        )

        sut.onAppear()

        assertEquals(
            "two reports about different fields are answered separately, so they must not be merged into one row",
            listOf("a", "b", "c"),
            sut.rows.value.map { it.id },
        )
    }

    @Test
    fun onAppear_resolvesItemNameFromCatalogue() = runTest {
        val item = makeItem(id = "111")
        val sut = makeSUT(reports = listOf(makeReport(id = "a", barcode = "111")), item = item)

        sut.onAppear()

        assertEquals(item.displayName, sut.rows.value.first().itemName)
    }

    @Test
    fun onAppear_whenItemNoLongerResolves_stillShowsTheRow() = runTest {
        val sut = makeSUT(reports = listOf(makeReport(id = "a", barcode = "111")), item = null)

        sut.onAppear()

        assertEquals("a report on a barcode deleted outside the app must still be answerable, not dropped", "111", sut.rows.value.first().report.barcode)
        assertNull(sut.rows.value.first().itemName)
    }

    @Test
    fun onAppear_whenLookupReturnsTheSameItemTwice_stillShowsEveryRow() = runTest {
        val sut = makeSUT(
            reports = listOf(makeReport(id = "a", barcode = "111"), makeReport(id = "b", barcode = "222")),
            item = makeItem(id = "111"),
        )

        sut.onAppear()

        assertEquals(2, sut.rows.value.size)
    }

    @Test
    fun onReportResolved_removesOnlyThatTicket() = runTest {
        val sut = makeSUT(reports = listOf(makeReport(id = "a", barcode = "111"), makeReport(id = "b", barcode = "111")))
        sut.onAppear()

        sut.onReportResolved("a")

        assertEquals("resolving one report says nothing about the other report of the same item", listOf("b"), sut.rows.value.map { it.id })
    }

    // MARK: - Helpers

    private fun makeSUT(
        reports: List<FoodItemReportDomain> = emptyList(),
        item: FoodItemDomain? = null,
    ): ModerationReportsViewModel = ModerationReportsViewModel(
        fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = reports) as FetchFoodItemReportsUseCaseProtocol,
        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item) as FetchFoodItemByBarcodeUseCaseProtocol,
    )

    private fun makeItem(id: String = "12345678", czName: String = "Tvaroh", date: Instant = Instant.now()): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = czName,
        engName = "Cottage cheese",
        weight = 200.0,
        date = date,
        energyKJ = 335.0,
        caloriesPerHundredGrams = 80.0,
        fat = 0.5,
        fatSaturated = 0.3,
        fatUnsaturatedFattyAcids = 0.2,
        carbohydrate = 4.0,
        carbohydratePureSugar = 3.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.1,
    )
}
