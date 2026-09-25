package antoni.kalorie.core.nutritionlabelrecognition

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

interface TextRecognizerProtocol {
    suspend fun recognizeText(image: NutritionLabelImage): List<RecognizedTextLine>
}

interface BarcodeDetectorProtocol {
    suspend fun detectBarcode(image: NutritionLabelImage): String?
}

class MlKitTextRecognizer : TextRecognizerProtocol, BarcodeDetectorProtocol {

    // MARK: - Properties

    private val textClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeClient = BarcodeScanning.getClient()

    // MARK: - Functions

    override suspend fun recognizeText(image: NutritionLabelImage): List<RecognizedTextLine> {
        val bitmap = image.toBitmap()
        val (uprightWidth, uprightHeight) = uprightSize(bitmap.width, bitmap.height, image.rotationDegrees)
        return recognizeLines(InputImage.fromBitmap(bitmap, image.rotationDegrees), uprightWidth, uprightHeight)
    }

    override suspend fun detectBarcode(image: NutritionLabelImage): String? =
        detectBarcode(InputImage.fromBitmap(image.toBitmap(), image.rotationDegrees))

    suspend fun recognizeLines(input: InputImage, uprightWidth: Int, uprightHeight: Int): List<RecognizedTextLine> {
        val result = textClient.process(input).await()
        return result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                normalizedLine(
                    text = line.text,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    imageWidth = uprightWidth,
                    imageHeight = uprightHeight,
                )
            }
    }

    suspend fun detectBarcode(input: InputImage): String? =
        barcodeClient.process(input).await().firstNotNullOfOrNull { it.rawValue }

    fun close() {
        textClient.close()
        barcodeClient.close()
    }
}

fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (rotationDegrees % 180 != 0) height to width else width to height

// ML Kit boxes are pixels with the origin top-left and y down; the parser expects Vision's
// normalized boxes with the origin bottom-left and y up.
fun normalizedLine(
    text: String,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    imageWidth: Int,
    imageHeight: Int,
): RecognizedTextLine = RecognizedTextLine(
    text = text,
    boundingBox = NormalizedRect(
        x = left.toDouble() / imageWidth,
        y = 1.0 - bottom.toDouble() / imageHeight,
        width = (right - left).toDouble() / imageWidth,
        height = (bottom - top).toDouble() / imageHeight,
    ),
)
