package antoni.kalorie.core.nutritionlabelrecognition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

sealed class NutritionLabelRecognitionError : Exception() {
    data object NothingRecognized : NutritionLabelRecognitionError()
}

interface RecognizeNutritionLabelUseCaseProtocol {
    suspend operator fun invoke(image: NutritionLabelImage): NutritionLabelReading
}

class RecognizeNutritionLabelUseCase(
    private val textRecognizer: TextRecognizerProtocol,
    private val barcodeDetector: BarcodeDetectorProtocol,
) : RecognizeNutritionLabelUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(image: NutritionLabelImage): NutritionLabelReading = coroutineScope {
        val linesTask = async { textRecognizer.recognizeText(image) }
        val barcodeTask = async {
            try {
                barcodeDetector.detectBarcode(image)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

        val lines = linesTask.await()
        val reading = NutritionLabelParser.parse(lines).copy(scannedCode = barcodeTask.await())

        if (reading.isEmpty) throw NutritionLabelRecognitionError.NothingRecognized
        reading
    }
}
