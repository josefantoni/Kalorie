package antoni.kalorie.core.models

import org.junit.Assert.assertEquals
import org.junit.Test

class BilingualNamedTest {

    // MARK: - Tests

    @Test
    fun displayName_onSlovakDevice_isTheSlovakName() {
        assertEquals("Mlieko", makeSUT().displayName("sk"))
    }

    @Test
    fun displayName_onCzechDevice_isTheCzechName() {
        assertEquals("Mléko", makeSUT().displayName("cs"))
    }

    @Test
    fun displayName_onEnglishDevice_isTheEnglishName() {
        assertEquals("Milk", makeSUT().displayName("en"))
    }

    @Test
    fun displayName_onEnglishDeviceWithoutEnglishName_fallsBackToTheCzechName() {
        assertEquals("Mléko", makeSUT(engName = "").displayName("en"))
    }

    @Test
    fun displayName_onCzechDeviceWithoutCzechName_fallsBackToTheSlovakNameBeforeEnglish() {
        assertEquals("Mlieko", makeSUT(czName = "").displayName("cs"))
    }

    @Test
    fun displayName_onSlovakDeviceWithoutSlovakName_fallsBackToTheCzechNameBeforeEnglish() {
        assertEquals("Mléko", makeSUT(skName = "").displayName("sk"))
    }

    @Test
    fun displayName_onEnglishDeviceWithOnlySlovakName_showsTheSlovakName() {
        assertEquals("Mlieko", makeSUT(czName = "", engName = "").displayName("en"))
    }

    // MARK: - Helpers

    private fun makeSUT(czName: String = "Mléko", skName: String = "Mlieko", engName: String = "Milk"): BilingualNamed = object : BilingualNamed {
        override val czName = czName
        override val skName = skName
        override val engName = engName
    }
}
