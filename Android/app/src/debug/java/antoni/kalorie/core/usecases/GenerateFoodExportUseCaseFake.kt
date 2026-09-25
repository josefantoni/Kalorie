package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.models.MealTypeDomain
import java.io.File
import java.time.Instant

data class GenerateFoodExportUseCaseFake(
    val stubbedFile: File = File("/dev/null"),
    val stubbedError: Exception? = null,
) : GenerateFoodExportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(from: Instant, to: Instant, format: FoodExportFormat, mealTypes: List<MealTypeDomain>): File {
        stubbedError?.let { throw it }
        return stubbedFile
    }
}
