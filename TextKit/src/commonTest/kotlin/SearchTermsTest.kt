package antoni.kalorie.textkit

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTermsTest {

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
