package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodItemFormInputApplyingTest {

    // MARK: - applying

    @Test
    fun applying_readingWithMillilitres_setsMeasureAndReportsItChanged() {
        val (result, applied) = FoodItemFormInput().applying(NutritionLabelReading(measure = FoodMeasure.MILLILITRES))

        assertEquals(FoodMeasure.MILLILITRES, result.measure)
        assertTrue(FoodItemFormField.MEASURE in applied)
    }

    @Test
    fun applying_whenFormAlreadyAtMillilitres_isNotOverwrittenByGrams() {
        val sut = FoodItemFormInput(measure = FoodMeasure.MILLILITRES)

        val (result, _) = sut.applying(NutritionLabelReading(measure = FoodMeasure.GRAMS))

        assertEquals(FoodMeasure.MILLILITRES, result.measure)
    }

    @Test
    fun applying_fillsOnlyEmptyFieldsAndReportsThem() {
        val sut = FoodItemFormInput(fat = 5.0)
        val reading = NutritionLabelReading(fat = 12.0, protein = 10.0)

        val (result, applied) = sut.applying(reading)

        assertEquals(5.0, result.fat, 0.0)
        assertEquals(10.0, result.protein, 0.0)
        assertEquals(setOf(FoodItemFormField.PROTEIN), applied)
    }

    @Test
    fun applying_neverOverwritesAFieldAnEarlierScanAlreadyRecognized() {
        val sut = FoodItemFormInput(salt = 0.0)

        val (result, applied) = sut.applying(
            reading = NutritionLabelReading(salt = 3.0),
            alreadyRecognizedFields = setOf(FoodItemFormField.SALT),
        )

        assertEquals(0.0, result.salt, 0.0)
        assertFalse(FoodItemFormField.SALT in applied)
    }

    @Test
    fun applying_doesNotChangeAnExistingBarcode() {
        val sut = FoodItemFormInput(scannedCode = "87654321")

        val (result, _) = sut.applying(NutritionLabelReading(scannedCode = "8594004428464", fat = 1.0))

        assertEquals("87654321", result.scannedCode)
    }

    @Test
    fun applying_weightAtOrAboveAThousand_isShownInThousands() {
        val (result, applied) = FoodItemFormInput().applying(NutritionLabelReading(weightOfProduct = 1500.0))

        assertEquals(1.5, result.weightOfProduct, 0.0)
        assertTrue(result.isWeightInThousands)
        assertTrue(FoodItemFormField.WEIGHT in applied)
    }

    @Test
    fun applying_portionsOnlyWhenTheFormHasNone() {
        val reading = NutritionLabelReading(portions = listOf(FoodPortionDomain(name = "1 balení", grams = 250.0)))

        val (filled, _) = FoodItemFormInput().applying(reading)
        val (kept, _) = FoodItemFormInput(portions = listOf(FoodPortionDraft(name = "Plátek", gramsText = "30"))).applying(reading)

        assertEquals(listOf("1 balení" to "250"), filled.portions.map { it.name to it.gramsText })
        assertEquals("Plátek", kept.portions.single().name)
    }
}
