package antoni.kalorie.core.models

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FoodPortionModelTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun displayName_inCzech_showsTheCzechPortionName() {
        Locale.setDefault(Locale.forLanguageTag("cs"))

        assertEquals("1 lžíce", FoodPortionDomain(name = "tbsp", grams = 15.0).displayName)
    }

    @Test
    fun displayName_inSlovak_showsTheCzechPortionName() {
        Locale.setDefault(Locale.forLanguageTag("sk"))

        assertEquals("1 lžíce", FoodPortionDomain(name = "tbsp", grams = 15.0).displayName)
    }

    @Test
    fun displayName_inEnglish_showsTheEnglishPortionName() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("1 tbsp", FoodPortionDomain(name = "1 lžíce", grams = 15.0).displayName)
    }

    @Test
    fun displayName_withAnUnknownName_keepsTheNameCapitalised() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("Hrnek", FoodPortionDomain(name = "hrnek", grams = 40.0).displayName)
    }
}
