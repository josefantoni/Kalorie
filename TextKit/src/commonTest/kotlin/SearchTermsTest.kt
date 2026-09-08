import kotlin.test.Test
import kotlin.test.assertEquals

// The exact-value case below mirrors TextKit/fixtures/text-kit-cases.json → searchTerms. Kept in
// sync by hand with the JS suite in scripts/lib/search-terms.test.js, which loads that file
// directly — TextKit has no JVM/JS test target able to read a repo file at test time. See
// ADR 0026.
class SearchTermsTest {

    @Test
    fun searchTerms_matchesSharedFixture() {
        assertEquals(listOf("t", "tv", "tva", "tvar", "tvaro", "tvaroh"), searchTerms("Tvaroh"))
        assertEquals(
            listOf(
                "p", "po", "pol", "polo", "polot", "polotu", "polotuc",
                "polotucn", "polotucne", "m", "ml", "mle", "mlek", "mleko"
            ),
            searchTerms("Polotučné mléko")
        )
        assertEquals(listOf("r", "ro", "roh", "rohl", "rohli", "rohlik"), searchTerms("ROHLÍK"))
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
