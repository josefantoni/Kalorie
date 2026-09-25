package antoni.kalorie.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodPortionValidationTest {

    // MARK: - validate(name:grams:)

    @Test
    fun validate_withEmptyName_returnsInvalidName() {
        assertEquals(FoodPortionError.InvalidName, FoodPortionValidation.validate(name = "", grams = 33.0))
    }

    @Test
    fun validate_withWhitespaceOnlyName_returnsInvalidName() {
        assertEquals(FoodPortionError.InvalidName, FoodPortionValidation.validate(name = "   ", grams = 33.0))
    }

    @Test
    fun validate_withGramsBelowOne_returnsInvalidGrams() {
        assertEquals(FoodPortionError.InvalidGrams, FoodPortionValidation.validate(name = "1 balení", grams = 0.5))
    }

    @Test
    fun validate_withNaNGrams_returnsInvalidGrams() {
        assertEquals(FoodPortionError.InvalidGrams, FoodPortionValidation.validate(name = "1 balení", grams = Double.NaN))
    }

    @Test
    fun validate_withValidNameAndGrams_returnsNil() {
        assertNull(FoodPortionValidation.validate(name = "1 balení", grams = 33.0))
    }

    // MARK: - validate(portions:)

    @Test
    fun validate_withPortionsAtLimit_returnsNil() {
        val portions = (0 until FoodPortionValidation.MAX_PORTIONS).map { FoodPortionDomain(name = "Porce $it", grams = 10.0) }
        assertNull(FoodPortionValidation.validate(portions = portions))
    }

    @Test
    fun validate_withPortionsOverLimit_returnsTooMany() {
        val portions = (0 until FoodPortionValidation.MAX_PORTIONS + 1).map { FoodPortionDomain(name = "Porce $it", grams = 10.0) }
        assertEquals(FoodPortionError.TooMany, FoodPortionValidation.validate(portions = portions))
    }
}
