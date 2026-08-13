fun minutesSinceMidnight(hour: Int, minute: Int): Int = hour * 60 + minute

fun isMinuteWithinWindow(minutes: Int, startMinutes: Int, endMinutes: Int): Boolean =
    minutes >= startMinutes && minutes < endMinutes

fun mealWindowsOverlap(startMinutes: Int, endMinutes: Int, otherStartMinutes: Int, otherEndMinutes: Int): Boolean =
    startMinutes < otherEndMinutes && endMinutes > otherStartMinutes

fun isMealWindowLongEnough(startMinutes: Int, endMinutes: Int, minimumDurationMinutes: Int): Boolean =
    endMinutes - startMinutes >= minimumDurationMinutes
