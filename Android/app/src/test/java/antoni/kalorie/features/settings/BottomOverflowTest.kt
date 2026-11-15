package antoni.kalorie.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomOverflowTest {

    @Test
    fun footerTallerThanViewportOverflowsByTheHiddenBottom() {
        val overflow = bottomOverflow(itemOffset = 0, itemSize = 220, viewportEnd = 150, afterContentPadding = 0)

        assertEquals(70, overflow)
    }

    @Test
    fun footerFittingTheViewportDoesNotOverflow() {
        val overflow = bottomOverflow(itemOffset = 40, itemSize = 100, viewportEnd = 200, afterContentPadding = 0)

        assertEquals(-60, overflow)
    }

    @Test
    fun contentPaddingBelowTheListShrinksTheVisibleArea() {
        val overflow = bottomOverflow(itemOffset = 0, itemSize = 150, viewportEnd = 150, afterContentPadding = 30)

        assertEquals(30, overflow)
    }
}
