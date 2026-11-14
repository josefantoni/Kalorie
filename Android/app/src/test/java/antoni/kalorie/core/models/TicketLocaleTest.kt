package antoni.kalorie.core.models

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketLocaleTest {

    // MARK: - Tests

    @Test
    fun current_mapsTheSupportedLanguagesToThemselves() {
        assertEquals("cs", TicketLocale.current("cs"))
        assertEquals("en", TicketLocale.current("en"))
        assertEquals("sk", TicketLocale.current("sk"))
    }

    @Test
    fun current_dropsTheRegion_andFallsBackToEnglishForAnythingElse() {
        assertEquals("cs", TicketLocale.current("cs-CZ"))
        assertEquals("the rules accept only cs, en and sk, so any other language must still be writable", "en", TicketLocale.current("de"))
    }

    @Test
    fun resolved_fallsBackToEnglishForAMissingOrUnknownStoredValue() {
        assertEquals("sk", TicketLocale.resolved("sk"))
        assertEquals("a ticket without a locale must still get a pre-filled response", "en", TicketLocale.resolved(null))
        assertEquals("en", TicketLocale.resolved("fr"))
    }
}
