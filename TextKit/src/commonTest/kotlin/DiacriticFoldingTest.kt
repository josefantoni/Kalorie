import kotlin.test.Test
import kotlin.test.assertEquals

class DiacriticFoldingTest {

    @Test
    fun foldDiacritics_withNoDiacritics_returnsInputUnchanged() {
        assertEquals("Plain text", foldDiacritics("Plain text"))
    }

    @Test
    fun foldDiacritics_foldsEachCzechDiacritic() {
        assertEquals("acdeenorstuuyz", foldDiacritics("áčďěéňóřšťúůýž"))
        assertEquals("ACDEENORSTUUYZ", foldDiacritics("ÁČĎĚÉŇÓŘŠŤÚŮÝŽ"))
    }

    @Test
    fun foldDiacritics_foldsDiacriticsWithinAWord() {
        assertEquals("rohlik", foldDiacritics("rohlík"))
        assertEquals("Polotucne mleko", foldDiacritics("Polotučné mléko"))
    }
}
