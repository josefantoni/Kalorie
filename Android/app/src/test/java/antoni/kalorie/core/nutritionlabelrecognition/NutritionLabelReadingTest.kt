package antoni.kalorie.core.nutritionlabelrecognition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelReadingTest {

    // MARK: - isCompleteForAutoCapture

    @Test
    fun isCompleteForAutoCapture_withKcalFatCarbsProtein_isTrue() {
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 370.0, fat = 12.0, carbohydrate = 55.0, protein = 10.0)

        assertTrue(reading.isCompleteForAutoCapture)
    }

    @Test
    fun isCompleteForAutoCapture_missingOneMacro_isFalse() {
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 370.0, fat = 12.0, carbohydrate = 55.0, protein = null)

        assertFalse(reading.isCompleteForAutoCapture)
    }

    @Test
    fun isCompleteForAutoCapture_barcodeOnly_isFalse() {
        val reading = NutritionLabelReading(scannedCode = "12345678")

        assertFalse(reading.isCompleteForAutoCapture)
    }
}
