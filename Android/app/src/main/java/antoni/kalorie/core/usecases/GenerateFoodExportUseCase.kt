package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.exportkit.renderPdf
import antoni.kalorie.exportkit.renderXlsx
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GenerateFoodExportUseCaseProtocol {
    suspend operator fun invoke(from: Instant, to: Instant, format: FoodExportFormat, mealTypes: List<MealTypeDomain>): File
}

class GenerateFoodExportUseCase(
    private val fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseProtocol,
    private val reportFactory: FoodExportReportFactory,
    private val directory: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : GenerateFoodExportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(from: Instant, to: Instant, format: FoodExportFormat, mealTypes: List<MealTypeDomain>): File {
        val foods = fetchFoodsConsumedInRange(from, to)
        val report = reportFactory.makeReport(foods = foods, mealTypes = mealTypes, from = from, to = to)
        return withContext(Dispatchers.Default) {
            val data = when (format) {
                FoodExportFormat.PDF -> renderPdf(report)
                FoodExportFormat.XLSX -> renderXlsx(report)
            }
            directory.mkdirs()
            directory.listFiles()?.forEach { it.delete() }
            File(directory, "Kalorie_${isoDay(from)}_${isoDay(to)}.${format.fileExtension}").also { it.writeBytes(data) }
        }
    }

    // MARK: - Private

    private fun isoDay(instant: Instant): String = instant.atZone(zone).toLocalDate().toString()
}
