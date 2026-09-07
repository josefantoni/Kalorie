import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTermsTest {

    @Test
    fun searchTerms_withSingleWord_returnsEveryPrefix() {
        assertEquals(listOf("t", "tv", "tva", "tvar", "tvaro", "tvaroh"), searchTerms("Tvaroh"))
    }

    @Test
    fun searchTerms_findsAnyWordByAnyOfItsPrefixes() {
        val terms = searchTerms("Polotučné mléko")
        assertEquals(true, terms.contains("mleko"))
        assertEquals(true, terms.contains("mlek"))
        assertEquals(true, terms.contains("m"))
        assertEquals(true, terms.contains("polotucne"))
    }

    @Test
    fun searchTerms_lowercasesAndFoldsDiacritics() {
        assertEquals(true, searchTerms("ROHLÍK").contains("rohlik"))
    }

    @Test
    fun searchTerms_collapsesRepeatedWhitespace_andHasNoEmptyTerms() {
        assertEquals(false, searchTerms("Tvaroh   light").contains(""))
    }

    @Test
    fun searchTerms_deduplicatesSharedPrefixesAcrossWords() {
        val terms = searchTerms("mléko mléko")
        assertEquals(terms.toSet().size, terms.size)
    }
}
