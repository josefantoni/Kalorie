package antoni.kalorie.features.dashboard

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightSparklineTest {

    // MARK: - Tests

    @Test
    fun newestWeight_isTheCentreOfTheGraph() {
        val positions = WeightSparkline(listOf(79.0, 79.4, 78.4), centerIndex = 2, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        assertEquals("the weight valid on the selected day is the dot in the middle", Offset(WIDTH / 2, HEIGHT / 2), positions.last())
    }

    @Test
    fun olderWeights_extendToTheLeftInOrder_oldestAtTheEdge() {
        val positions = WeightSparkline(listOf(79.0, 79.4, 78.4), centerIndex = 2, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        assertEquals(0f, positions.first().x, 0f)
        assertEquals(positions.map { it.x }, positions.map { it.x }.sorted())
        assertTrue(positions.all { it.x <= WIDTH / 2 })
    }

    @Test
    fun smallWobble_isNotDrawnAsACliff_becauseOfTheMinimumSpan() {
        val positions = WeightSparkline(listOf(78.6, 78.4), centerIndex = 1, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        val drop = kotlin.math.abs(positions.first().y - HEIGHT / 2)
        assertTrue("0.2 kg on a 2 kg minimum span must stay a gentle slope", drop < HEIGHT / 2 * 0.25f)
    }

    @Test
    fun higherEarlierWeight_isDrawnAboveTheDot() {
        val positions = WeightSparkline(listOf(80.0, 78.4), centerIndex = 1, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        assertTrue(positions[0].y < positions[1].y)
    }

    @Test
    fun laterWeights_extendToTheRightOfTheDot() {
        val positions = WeightSparkline(listOf(79.0, 78.4, 77.9, 77.5), centerIndex = 1, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        assertEquals(Offset(WIDTH / 2, HEIGHT / 2), positions[1])
        assertTrue("entries after the selected day belong on the right", positions[2].x > WIDTH / 2 && positions[3].x > positions[2].x)
        assertTrue(positions.last().x <= WIDTH)
    }

    @Test
    fun onlyLaterWeights_dotStaysCentredAndLineRunsRight() {
        val positions = WeightSparkline(listOf(78.4, 77.9), centerIndex = 0, minimumSpan = 2.0).positions(WIDTH, HEIGHT)

        assertEquals(Offset(WIDTH / 2, HEIGHT / 2), positions[0])
        assertEquals(WIDTH, positions[1].x, 0f)
    }

    @Test
    fun singleWeight_drawsNothing() {
        assertTrue(WeightSparkline(listOf(78.4), centerIndex = 0, minimumSpan = 2.0).positions(WIDTH, HEIGHT).isEmpty())
    }

    private companion object {
        const val WIDTH = 56f
        const val HEIGHT = 16f
    }
}
