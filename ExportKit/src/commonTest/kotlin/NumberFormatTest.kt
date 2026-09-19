import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
    @Test
    fun usesTheGivenDecimalSeparator() {
        assertEquals("2,5", formatNumber(2.5, 1, ","))
        assertEquals("2.5", formatNumber(2.5, 1, "."))
    }

    @Test
    fun alwaysShowsTheRequestedFractionDigits() {
        assertEquals("2,0", formatNumber(2.0, 1, ","))
        assertEquals("0,05", formatNumber(0.05, 2, ","))
    }

    @Test
    fun roundsHalfAwayFromZeroAndCarriesIntoTheWholePart() {
        assertEquals("1,3", formatNumber(1.25, 1, ","))
        assertEquals("10,0", formatNumber(9.96, 1, ","))
    }

    @Test
    fun zeroFractionDigitsHasNoSeparator() {
        assertEquals("1235", formatNumber(1234.5, 0, ","))
        assertEquals("0", formatNumber(0.4, 0, ","))
    }

    @Test
    fun aValueRoundingToZeroHasNoMinusSign() {
        assertEquals("0,0", formatNumber(-0.04, 1, ","))
        assertEquals("-1,5", formatNumber(-1.5, 1, ","))
    }
}
