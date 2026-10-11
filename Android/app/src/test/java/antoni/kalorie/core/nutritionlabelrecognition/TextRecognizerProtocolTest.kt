package antoni.kalorie.core.nutritionlabelrecognition

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class TextRecognizerProtocolTest {

    // MARK: - Tests

    @Test
    fun angleDegrees_cornersOfUprightText_giveZero() {
        assertEquals(0.0, angleDegrees(20, 100, 200, 100), ACCURACY)
    }

    @Test
    fun angleDegrees_cornersOfUpsideDownText_giveHalfTurn() {
        assertEquals(180.0, abs(angleDegrees(200, 140, 20, 140)), ACCURACY)
    }

    // MARK: - Helpers

    private companion object {
        const val ACCURACY = 1e-6
    }
}
