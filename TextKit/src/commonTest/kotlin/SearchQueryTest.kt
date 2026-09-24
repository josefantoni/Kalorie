package antoni.kalorie.textkit

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
