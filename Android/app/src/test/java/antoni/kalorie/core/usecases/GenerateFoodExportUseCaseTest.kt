package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.utils.StringProviderFake
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerateFoodExportUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // MARK: - Tests

    @Test
    fun generate_whenFetchFails_rethrowsItAndWritesNoFile() = runTest {
        val (sut, directory) = makeSUT(fetch = FetchFoodsConsumedInRangeUseCaseFake(stubbedError = AuthError.NotAuthenticated))

        try {
            sut(from = Instant.now(), to = Instant.now(), format = FoodExportFormat.XLSX, mealTypes = emptyList())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun generate_xlsx_writesAZipContainerNamedAfterTheInterval() = runTest {
        val (sut, _) = makeSUT()

        val file = sut(from = makeDate(1), to = makeDate(19), format = FoodExportFormat.XLSX, mealTypes = emptyList())

        assertEquals("Kalorie_2026-09-01_2026-09-19.xlsx", file.name)
        assertEquals(listOf<Byte>(0x50, 0x4B, 0x03, 0x04), file.readBytes().take(4))
    }

    @Test
    fun generate_withNoEntriesInTheInterval_stillProducesAFile() = runTest {
        val (sut, _) = makeSUT(fetch = FetchFoodsConsumedInRangeUseCaseFake(stubbedFoods = emptyList()))

        val file = sut(from = makeDate(1), to = makeDate(3), format = FoodExportFormat.XLSX, mealTypes = emptyList())

        assertTrue(file.exists())
    }

    @Test
    fun generate_removesTheFileOfAnEarlierExport() = runTest {
        val (sut, directory) = makeSUT()
        sut(from = makeDate(1), to = makeDate(2), format = FoodExportFormat.XLSX, mealTypes = emptyList())

        sut(from = makeDate(3), to = makeDate(4), format = FoodExportFormat.XLSX, mealTypes = emptyList())

        assertEquals(listOf("Kalorie_2026-09-03_2026-09-04.xlsx"), directory.listFiles().orEmpty().map { it.name })
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetch: FetchFoodsConsumedInRangeUseCaseFake = FetchFoodsConsumedInRangeUseCaseFake(),
    ): Pair<GenerateFoodExportUseCase, File> {
        val directory = File(temporaryFolder.newFolder(), "exports")
        val factory = FoodExportReportFactory(strings = StringProviderFake(), zone = ZONE, locale = Locale.ENGLISH)
        return GenerateFoodExportUseCase(fetch, factory, directory, ZONE) to directory
    }

    private fun makeDate(day: Int): Instant = ZonedDateTime.of(2026, 9, day, 12, 0, 0, 0, ZONE).toInstant()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}
