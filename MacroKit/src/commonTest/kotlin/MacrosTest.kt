import kotlin.test.Test
import kotlin.test.assertEquals

class MacrosTest {

    private fun macros(
        calories: Int = 100,
        protein: Double = 10.0,
        carbohydrate: Double = 20.0,
        carbohydrateSugar: Double = 5.0,
        fat: Double = 2.0,
        fatUnsaturated: Double = 1.0,
        fiber: Double = 3.0,
        salt: Double = 0.5,
    ) = Macros(
        calories = calories,
        protein = protein,
        carbohydrate = carbohydrate,
        carbohydrateSugar = carbohydrateSugar,
        fat = fat,
        fatUnsaturated = fatUnsaturated,
        fiber = fiber,
        salt = salt,
    )

    @Test
    fun scaled_byOne_returnsEqualValues() {
        val result = macros().scaled(1.0)
        assertEquals(macros(), result)
    }

    @Test
    fun scaled_byHalf_halvesAllFields() {
        val result = macros(calories = 100, protein = 10.0).scaled(0.5)
        assertEquals(50, result.calories)
        assertEquals(5.0, result.protein)
    }

    @Test
    fun scaled_whenResultIsFractional_roundsHalfAwayFromZero() {
        val result = macros(calories = 133).scaled(1.5)
        assertEquals(200, result.calories)
    }

    @Test
    fun scaled_doesNotRoundDoubleFields() {
        val result = macros(protein = 13.0).scaled(1.5)
        assertEquals(19.5, result.protein)
    }

    @Test
    fun scaledCalories_roundsTheProductOnce_notTheRateFirst() {
        assertEquals(200, scaledCalories(caloriesPerHundredGrams = 133.6, ratio = 1.5))
    }

    @Test
    fun scaledCalories_withIntegerRate_matchesSimpleMultiplication() {
        assertEquals(400, scaledCalories(caloriesPerHundredGrams = 200.0, ratio = 2.0))
    }

    @Test
    fun total_ofEmptyList_returnsZeroMacros() {
        val result = emptyList<Macros>().total()
        assertEquals(0, result.calories)
        assertEquals(0.0, result.protein)
    }

    @Test
    fun total_sumsAllFieldsAcrossList() {
        val result = listOf(
            macros(calories = 100, protein = 10.0),
            macros(calories = 50, protein = 5.0),
        ).total()
        assertEquals(150, result.calories)
        assertEquals(15.0, result.protein)
    }
}
