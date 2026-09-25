package antoni.kalorie.core.nutritionlabelrecognition

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RecognizeNutritionLabelUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenNothingRecognized_throwsNothingRecognized() = runTest {
        val sut = makeSUT(textLines = emptyList(), barcode = null)

        try {
            sut(FakeImage)
            fail("expected nothingRecognized to be thrown")
        } catch (_: NutritionLabelRecognitionError.NothingRecognized) {
        }
    }

    @Test
    fun invoke_whenOnlyABarcodeIsFound_returnsIt() = runTest {
        val sut = makeSUT(textLines = emptyList(), barcode = "12345678")

        val reading = sut(FakeImage)

        assertEquals("12345678", reading.scannedCode)
    }

    @Test
    fun invoke_parsesTheRecognizedRows() = runTest {
        val lines = listOf(
            RecognizedTextLine("100 g", NormalizedRect(0.6, 0.9, 0.15, 0.03)),
            RecognizedTextLine("Tuky", NormalizedRect(0.1, 0.7, 0.3, 0.05)),
            RecognizedTextLine("12 g", NormalizedRect(0.6, 0.7, 0.15, 0.05)),
        )
        val sut = makeSUT(textLines = lines, barcode = null)

        val reading = sut(FakeImage)

        assertEquals(12.0, reading.fat)
    }

    @Test
    fun invoke_whenBarcodeDetectionFails_stillReturnsTheParsedRows() = runTest {
        val lines = listOf(
            RecognizedTextLine("100 g", NormalizedRect(0.6, 0.9, 0.15, 0.03)),
            RecognizedTextLine("Tuky", NormalizedRect(0.1, 0.7, 0.3, 0.05)),
            RecognizedTextLine("12 g", NormalizedRect(0.6, 0.7, 0.15, 0.05)),
        )
        val sut = RecognizeNutritionLabelUseCase(
            textRecognizer = TextRecognizerFake(stubbedLines = lines),
            barcodeDetector = BarcodeDetectorFake(errorToThrow = RuntimeException("no barcode")),
        )

        val reading = sut(FakeImage)

        assertEquals(12.0, reading.fat)
        assertEquals(null, reading.scannedCode)
    }

    @Test
    fun normalizedLine_flipsTheYAxisIntoVisionsBottomLeftOrigin() {
        val line = normalizedLine("Tuky", left = 100, top = 0, right = 300, bottom = 100, imageWidth = 1000, imageHeight = 1000)

        assertEquals(NormalizedRect(x = 0.1, y = 0.9, width = 0.2, height = 0.1), line.boundingBox)
    }

    @Test
    fun normalizedLine_keepsAHigherRowAboveALowerOne() {
        val upper = normalizedLine("Tuky", left = 0, top = 100, right = 100, bottom = 150, imageWidth = 1000, imageHeight = 1000)
        val lower = normalizedLine("Sacharidy", left = 0, top = 300, right = 100, bottom = 350, imageWidth = 1000, imageHeight = 1000)

        assertEquals(true, upper.boundingBox.midY > lower.boundingBox.midY)
    }

    // MARK: - Helpers

    private fun makeSUT(textLines: List<RecognizedTextLine>, barcode: String?): RecognizeNutritionLabelUseCase =
        RecognizeNutritionLabelUseCase(
            textRecognizer = TextRecognizerFake(stubbedLines = textLines),
            barcodeDetector = BarcodeDetectorFake(stubbedBarcode = barcode),
        )

    private object FakeImage : NutritionLabelImage {
        override val rotationDegrees: Int = 0

        override fun toBitmap(): Bitmap = error("the fakes never read the bitmap")
    }
}
