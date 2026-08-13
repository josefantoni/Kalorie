import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntitiesTest {

    @Test
    fun decodeHtmlEntities_withNoEntities_returnsInputUnchanged() {
        assertEquals("Plain text", decodeHtmlEntities("Plain text"))
    }

    @Test
    fun decodeHtmlEntities_decodesEachKnownEntity() {
        assertEquals("&", decodeHtmlEntities("&amp;"))
        assertEquals("\"", decodeHtmlEntities("&quot;"))
        assertEquals("<", decodeHtmlEntities("&lt;"))
        assertEquals(">", decodeHtmlEntities("&gt;"))
        assertEquals("'", decodeHtmlEntities("&apos;"))
        assertEquals("'", decodeHtmlEntities("&#39;"))
        assertEquals(" ", decodeHtmlEntities("&nbsp;"))
    }

    @Test
    fun decodeHtmlEntities_decodesMultipleEntitiesInOneString() {
        assertEquals("Ben & Jerry's \"famous\"", decodeHtmlEntities("Ben &amp; Jerry&#39;s &quot;famous&quot;"))
    }
}
