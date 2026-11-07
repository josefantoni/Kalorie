package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.displayName
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
    fun onAppear_groupsReportsByBarcode() = runTest {
        val reports = listOf(
            makeReport(barcode = "111", reportedBy = "user-1"),
            makeReport(barcode = "111", reportedBy = "user-2"),
            makeReport(barcode = "222", reportedBy = "user-3"),
        )
        val sut = makeSUT(fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = reports))

        sut.onAppear()

        val group111 = sut.groups.value.first { it.barcode == "111" }
        assertEquals(2, group111.reports.size)
        assertEquals(2, sut.groups.value.size)
    }

    @Test
    fun onAppear_ordersGroupsByReportCountDescending() = runTest {
        val reports = listOf(
            makeReport(barcode = "111", reportedBy = "user-1"),
            makeReport(barcode = "222", reportedBy = "user-2"),
            makeReport(barcode = "222", reportedBy = "user-3"),
            makeReport(barcode = "222", reportedBy = "user-4"),
        )
        val sut = makeSUT(fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = reports))

        sut.onAppear()

        assertEquals("222", sut.groups.value.first().barcode)
    }

    @Test
    fun onAppear_resolvesItemNameFromCatalogue() = runTest {
        val item = makeItem(id = "111")
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport(barcode = "111", reportedBy = "user-1"))),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item),
        )

        sut.onAppear()

        assertEquals(item.displayName, sut.groups.value.first().itemName)
    }

    @Test
    fun onAppear_whenItemNoLongerResolves_stillShowsTheGroup() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport(barcode = "111", reportedBy = "user-1"))),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null),
        )

        sut.onAppear()

        assertEquals("111", sut.groups.value.first().barcode)
        assertNull(sut.groups.value.first().itemName)
    }

    @Test
    fun onAppear_whenLookupReturnsTheSameItemTwice_stillShowsEveryGroup() = runTest {
        val reports = listOf(
            makeReport(barcode = "111", reportedBy = "user-1"),
            makeReport(barcode = "222", reportedBy = "user-1"),
        )
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = reports),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem(id = "111")),
        )

        sut.onAppear()

        assertEquals(2, sut.groups.value.size)
    }

    @Test
    fun onGroupReportsChanged_withNoRemainingReports_removesOnlyThatBarcodesGroup() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(
                    makeReport(barcode = "111", reportedBy = "user-1"),
                    makeReport(barcode = "222", reportedBy = "user-2"),
                ),
            ),
        )
        sut.onAppear()

        sut.onGroupReportsChanged("111", emptyList())

        assertEquals(listOf("222"), sut.groups.value.map { it.barcode })
    }

    @Test
    fun onGroupReportsChanged_withRemainingReports_keepsTheGroupWithOnlyThose() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(
                    makeReport(barcode = "111", reportedBy = "user-1"),
                    makeReport(barcode = "111", reportedBy = "user-2"),
                    makeReport(barcode = "111", reportedBy = "user-3"),
                ),
            ),
        )
        sut.onAppear()

        sut.onGroupReportsChanged("111", listOf(makeReport(barcode = "111", reportedBy = "user-3")))

        assertEquals(listOf("user-3"), sut.groups.value.single().reports.map { it.reportedBy })
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol = FetchFoodItemReportsUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
    ): ModerationReportsViewModel = ModerationReportsViewModel(
        fetchFoodItemReports = fetchFoodItemReports,
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
    )

    private fun makeReport(barcode: String, reportedBy: String): FoodItemReportDomain = FoodItemReportDomain(barcode = barcode, reportedBy = reportedBy, reason = "wrong data", reportedAt = Instant.now())

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
