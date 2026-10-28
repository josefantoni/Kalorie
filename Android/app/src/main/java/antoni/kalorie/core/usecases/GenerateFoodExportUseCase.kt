package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.exportkit.renderPdf
import antoni.kalorie.exportkit.renderXlsx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

interface GenerateFoodExportUseCaseProtocol {
    suspend operator fun invoke(from: Instant, to: Instant, format: FoodExportFormat, mealTypes: List<MealTypeDomain>): File
}

class GenerateFoodExportUseCase(
    private val fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseProtocol,
    private val fetchBodyWeights: FetchBodyWeightsUseCaseProtocol,
    private val fetchAppSettings: FetchAppSettingsUseCaseProtocol,
    private val reportFactory: FoodExportReportFactory,
    private val directory: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : GenerateFoodExportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(from: Instant, to: Instant, format: FoodExportFormat, mealTypes: List<MealTypeDomain>): File {
        val (foods, weights) = coroutineScope {
            val foodsRead = async { fetchFoodsConsumedInRange(from, to) }
            val weightsRead = async { fetchBodyWeights.between(from, to) }
            foodsRead.await() to weightsRead.await()
        }
        val unit = if (weights.isEmpty()) BodyWeightUnit.KG else readWeightUnit()
        val report = reportFactory.makeReport(foods = foods, mealTypes = mealTypes, from = from, to = to, weights = weights, weightUnit = unit)
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

    private suspend fun readWeightUnit(): BodyWeightUnit = try {
        fetchAppSettings().bodyWeightUnit
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.warning(error, Constants.LogCategory.EXPORT)
        BodyWeightUnit.KG
    }

    private fun isoDay(instant: Instant): String = instant.atZone(zone).toLocalDate().toString()
}
