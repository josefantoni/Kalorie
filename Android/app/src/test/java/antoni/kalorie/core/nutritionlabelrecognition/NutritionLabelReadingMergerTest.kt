package antoni.kalorie.core.nutritionlabelrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelReadingMergerTest {

    // MARK: - Tests

    @Test
    fun merge_valueSeenInTwoFrames_winsOverOneMisreadFrame() {
        val readings = listOf(
            NutritionLabelReading(carbohydrate = 15.0, fat = 0.1),
            NutritionLabelReading(carbohydrate = 150.0, fat = 0.1),
            NutritionLabelReading(carbohydrate = 15.0),
        )

        val merged = NutritionLabelReadingMerger.merge(readings)

        assertEquals(15.0, merged.carbohydrate ?: Double.NaN, 0.0)
        assertEquals(0.1, merged.fat ?: Double.NaN, 0.0)
    }

    @Test
    fun merge_valueSeenOnce_isDroppedBecauseNothingConfirmsIt() {
        val readings = listOf(
            NutritionLabelReading(fiber = 14.0),
            NutritionLabelReading(fiber = 1.4),
            NutritionLabelReading(),
        )

        assertNull(NutritionLabelReadingMerger.merge(readings).fiber)
    }

    @Test
    fun merge_framesDisagreeEverywhere_isEmpty() {
        val readings = listOf(
            NutritionLabelReading(protein = 0.5),
            NutritionLabelReading(protein = 5.0),
            NutritionLabelReading(protein = 50.0),
        )

        assertTrue(NutritionLabelReadingMerger.merge(readings).isEmpty)
    }

    @Test
    fun merge_noFrames_isEmpty() {
        assertTrue(NutritionLabelReadingMerger.merge(emptyList()).isEmpty)
    }
}
