package antoni.kalorie.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DateExtensionTest {

    // MARK: - Tests

    @Test
    fun epochSecondsExact_roundTripsSecondsThatCarryMoreThanMillisecondPrecision() {
        val seconds = listOf(1_758_800_000.123456, 1_758_800_000.9999995, 1_758_800_000.5000004, 1_758_800_000.0)

        seconds.forEach {
            assertEquals(it, instantFromEpochSecondsExact(it).epochSecondsAsExactDouble(), 0.0)
        }
    }
}
