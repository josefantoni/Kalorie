package antoni.kalorie.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoSuggestionDomainTest {

    // MARK: - Tests

    @Test
    fun id_joinsItemAndUser_asTheRulesExpectIt() {
        assertEquals("12345678_alice", PhotoSuggestionDomain.id("12345678", "alice"))
    }

    @Test
    fun storagePath_isThePrivateSuggestionFolderOfTheAuthor() {
        assertEquals("photoSuggestions/alice/12345678.jpg", PhotoSuggestionDomain.storagePath("12345678", "alice"))
    }

    @Test
    fun ids_splitAtTheFirstSeparator_soAUserIdWithUnderscoresSurvives() {
        assertEquals("12345678" to "al_ice", PhotoSuggestionDomain.ids("12345678_al_ice"))
    }

    @Test
    fun ids_withoutSeparatorOrWithEmptyPart_isNull() {
        assertNull(PhotoSuggestionDomain.ids("12345678"))
        assertNull(PhotoSuggestionDomain.ids("_alice"))
        assertNull(PhotoSuggestionDomain.ids("12345678_"))
    }
}
