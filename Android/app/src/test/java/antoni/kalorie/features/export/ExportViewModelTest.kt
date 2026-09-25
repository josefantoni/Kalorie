package antoni.kalorie.features.export

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.usecases.GenerateFoodExportUseCaseFake
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportViewModelTest {

    // MARK: - init

    @Test
    fun init_defaultsToTheCurrentMonthUpToNow() {
        val now = ZonedDateTime.of(2026, 9, 25, 14, 30, 0, 0, ZONE).toInstant()

        val sut = makeSUT(now = now)

        assertEquals(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZONE).toInstant(), sut.fromDate.value)
        assertEquals(now, sut.toDate.value)
        assertEquals(FoodExportFormat.PDF, sut.format.value)
    }

    // MARK: - isExportDisabled

    @Test
    fun isExportDisabled_whenFromIsAfterTo_isTrue() {
        val sut = makeSUT()
        sut.fromDate.value = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, ZONE).toInstant()
        sut.toDate.value = ZonedDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZONE).toInstant()

        assertTrue(sut.isExportDisabled)
    }

    @Test
    fun isExportDisabled_whenFromAndToAreOnTheSameDay_isFalse() {
        val sut = makeSUT()
        sut.fromDate.value = ZonedDateTime.of(2026, 9, 10, 0, 0, 0, 0, ZONE).toInstant()
        sut.toDate.value = ZonedDateTime.of(2026, 9, 10, 23, 0, 0, 0, ZONE).toInstant()

        assertFalse(sut.isExportDisabled)
    }

    // MARK: - onExportTapped

    @Test
    fun onExportTapped_whenSucceeds_publishesTheFileWithItsFormat() = runTest {
        val file = File("/tmp/export.xlsx")
        val sut = makeSUT(generateFoodExport = GenerateFoodExportUseCaseFake(stubbedFile = file))
        sut.format.value = FoodExportFormat.XLSX

        sut.onExportTapped()

        assertEquals(ExportedFile(file, FoodExportFormat.XLSX), sut.exportedFile.value)
        assertEquals(ExportViewModel.State.IDLE, sut.state.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onExportTapped_whenGenerationFails_showsAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(generateFoodExport = GenerateFoodExportUseCaseFake(stubbedError = RuntimeException("unknown")))

        sut.onExportTapped()

        assertEquals(R.string.common_error_unknown, sut.alertItem.value?.titleRes)
        assertNull(sut.exportedFile.value)
        assertEquals(ExportViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onExportTapped_whenFromIsAfterTo_doesNothing() = runTest {
        val sut = makeSUT()
        sut.fromDate.value = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, ZONE).toInstant()
        sut.toDate.value = ZonedDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZONE).toInstant()

        sut.onExportTapped()

        assertNull(sut.exportedFile.value)
    }

    @Test
    fun onShareFinished_clearsTheExportedFile() = runTest {
        val sut = makeSUT()
        sut.onExportTapped()

        sut.onShareFinished()

        assertNull(sut.exportedFile.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        generateFoodExport: GenerateFoodExportUseCaseFake = GenerateFoodExportUseCaseFake(),
        now: Instant = ZonedDateTime.of(2026, 9, 25, 12, 0, 0, 0, ZONE).toInstant(),
    ): ExportViewModel = ExportViewModel(mealTypes = emptyList(), generateFoodExport = generateFoodExport, now = now, zone = ZONE)

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}
