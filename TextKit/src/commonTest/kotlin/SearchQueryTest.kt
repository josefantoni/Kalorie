package antoni.kalorie.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {

    @Test
    fun lastWord_isTheLastFoldedWord() {
        assertEquals("ml", searchQuery("Polotučné ml").lastWord)
    }

    @Test
    fun lastWord_ignoresTrailingSpace() {
        assertEquals("mleko", searchQuery("mléko ").lastWord)
    }

    @Test
    fun lastWord_withOnlySpaces_fallsBackToTheWholeFoldedQuery() {
        assertEquals("   ", searchQuery("   ").lastWord)
    }

    @Test
    fun query_isLowercasedAndFolded() {
        val query = searchQuery("ROHLÍK")
        assertEquals("rohlík", query.lowercased)
        assertEquals("rohlik", query.folded)
        assertEquals("rohlik", query.lastWord)
    }

    @Test
    fun matches_foldedQueryFindsAccentedName() {
        assertTrue(matchesSearchQuery("Rohlík", searchQuery("rohlik")))
    }

    @Test
    fun matches_accentedQueryFindsPlainName() {
        assertTrue(matchesSearchQuery("Rohlik", searchQuery("rohlík")))
    }

    @Test
    fun matches_nonFirstWordThroughTheTokens() {
        assertTrue(matchesSearchQuery("Polotučné mléko", searchQuery("mlék")))
    }

    @Test
    fun matches_fullNamePrefixWithMultiWordQuery() {
        assertTrue(matchesSearchQuery("Polotučné mléko", searchQuery("polotučné ml")))
    }

    @Test
    fun matches_isPrefixPerWordNotSubstring() {
        assertFalse(matchesSearchQuery("Polotučné mléko", searchQuery("léko")))
    }

    @Test
    fun matches_emptyQueryMatchesEverything() {
        assertTrue(matchesSearchQuery("Rohlík", searchQuery("")))
    }
}
