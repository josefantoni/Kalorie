package antoni.kalorie.mealkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealWindowsTest {

    @Test
    fun minutesSinceMidnight_convertsHourAndMinute() {
        assertEquals(0, minutesSinceMidnight(hour = 0, minute = 0))
        assertEquals(90, minutesSinceMidnight(hour = 1, minute = 30))
        assertEquals(1439, minutesSinceMidnight(hour = 23, minute = 59))
    }

    @Test
    fun isMinuteWithinWindow_atStart_isIncluded() {
        assertTrue(isMinuteWithinWindow(minutes = 480, startMinutes = 480, endMinutes = 720))
    }

    @Test
    fun isMinuteWithinWindow_atEnd_isExcluded() {
        assertFalse(isMinuteWithinWindow(minutes = 720, startMinutes = 480, endMinutes = 720))
    }

    @Test
    fun isMinuteWithinWindow_outsideRange_isExcluded() {
        assertFalse(isMinuteWithinWindow(minutes = 420, startMinutes = 480, endMinutes = 720))
    }

    @Test
    fun mealWindowsOverlap_whenDisjoint_returnsFalse() {
        assertFalse(mealWindowsOverlap(startMinutes = 480, endMinutes = 540, otherStartMinutes = 540, otherEndMinutes = 600))
    }

    @Test
    fun mealWindowsOverlap_whenPartiallyOverlapping_returnsTrue() {
        assertTrue(mealWindowsOverlap(startMinutes = 420, endMinutes = 480, otherStartMinutes = 360, otherEndMinutes = 540))
    }

    @Test
    fun mealWindowsOverlap_whenOneWraps_theOther_returnsTrue() {
        assertTrue(mealWindowsOverlap(startMinutes = 420, endMinutes = 840, otherStartMinutes = 540, otherEndMinutes = 720))
    }

    @Test
    fun isMealWindowLongEnough_atExactMinimum_returnsTrue() {
        assertTrue(isMealWindowLongEnough(startMinutes = 480, endMinutes = 510, minimumDurationMinutes = 30))
    }

    @Test
    fun isMealWindowLongEnough_withoutExplicitMinimum_usesThirtyMinutes() {
        assertEquals(30, MIN_MEAL_WINDOW_MINUTES)
        assertTrue(isMealWindowLongEnough(startMinutes = 480, endMinutes = 510))
        assertFalse(isMealWindowLongEnough(startMinutes = 480, endMinutes = 509))
    }

    @Test
    fun isMealWindowLongEnough_belowMinimum_returnsFalse() {
        assertFalse(isMealWindowLongEnough(startMinutes = 480, endMinutes = 500, minimumDurationMinutes = 30))
    }

    @Test
    fun isMealWindowLongEnough_wrapsMidnight_countsBothSides() {
        // 23:50 -> 00:20 is 30 minutes, not a negative duration.
        assertTrue(isMealWindowLongEnough(startMinutes = 1430, endMinutes = 20, minimumDurationMinutes = 30))
    }

    @Test
    fun isMealWindowLongEnough_wrapsMidnight_belowMinimum_returnsFalse() {
        assertFalse(isMealWindowLongEnough(startMinutes = 1430, endMinutes = 10, minimumDurationMinutes = 30))
    }

    @Test
    fun isMinuteWithinWindow_wrapsMidnight_beforeMidnight_isIncluded() {
        assertTrue(isMinuteWithinWindow(minutes = 1430, startMinutes = 1380, endMinutes = 60))
    }

    @Test
    fun isMinuteWithinWindow_wrapsMidnight_afterMidnight_isIncluded() {
        assertTrue(isMinuteWithinWindow(minutes = 30, startMinutes = 1380, endMinutes = 60))
    }

    @Test
    fun isMinuteWithinWindow_wrapsMidnight_outsideWindow_isExcluded() {
        assertFalse(isMinuteWithinWindow(minutes = 720, startMinutes = 1380, endMinutes = 60))
    }

    @Test
    fun mealWindowsOverlap_wrapsMidnight_overlapsWindowAfterMidnight_returnsTrue() {
        // 23:50 -> 00:20 overlaps 00:00 -> 00:10.
        assertTrue(mealWindowsOverlap(startMinutes = 1430, endMinutes = 20, otherStartMinutes = 0, otherEndMinutes = 10))
    }

    @Test
    fun mealWindowsOverlap_wrapsMidnight_disjointFromLaterWindow_returnsFalse() {
        assertFalse(mealWindowsOverlap(startMinutes = 1430, endMinutes = 20, otherStartMinutes = 60, otherEndMinutes = 120))
    }

    private val breakfast = MealWindow(id = "breakfast", startMinutes = 360, endMinutes = 600)
    private val lunch = MealWindow(id = "lunch", startMinutes = 660, endMinutes = 840)
    private val night = MealWindow(id = "night", startMinutes = 1320, endMinutes = 120)

    @Test
    fun resolvedMealWindowId_pinToExistingWindow_winsOverTime() {
        val result = resolvedMealWindowId(minutes = 400, pinnedId = "lunch", windows = listOf(breakfast, lunch))
        assertEquals("lunch", result)
    }

    @Test
    fun resolvedMealWindowId_pinToDeletedWindow_fallsBackToTime() {
        val result = resolvedMealWindowId(minutes = 400, pinnedId = "deleted", windows = listOf(breakfast, lunch))
        assertEquals("breakfast", result)
    }

    @Test
    fun resolvedMealWindowId_noPinOutsideEveryWindow_isNull() {
        assertNull(resolvedMealWindowId(minutes = 620, pinnedId = null, windows = listOf(breakfast, lunch)))
    }

    @Test
    fun mealWindowAt_overlappingWindows_earliestStartWinsRegardlessOfListOrder() {
        val early = MealWindow(id = "early", startMinutes = 360, endMinutes = 700)
        val late = MealWindow(id = "late", startMinutes = 480, endMinutes = 720)
        assertEquals("early", mealWindowAt(minutes = 500, windows = listOf(late, early))?.id)
        assertEquals("early", mealWindowAt(minutes = 500, windows = listOf(early, late))?.id)
    }

    @Test
    fun mealWindowAt_windowWrappingMidnight_matchesBothSides() {
        val windows = listOf(breakfast, night)
        assertEquals("night", mealWindowAt(minutes = 1400, windows = windows)?.id)
        assertEquals("night", mealWindowAt(minutes = 60, windows = windows)?.id)
        assertNull(mealWindowAt(minutes = 200, windows = windows))
    }
}
