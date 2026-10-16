package antoni.kalorie.components.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.random.Random

data class BaguetteDrop(
    val id: Int,
    val delay: Double,
    val fallDuration: Double,
    val cycle: Double,
) {

    // MARK: - Functions

    fun xFraction(fall: Int): Float = 0.02f + 0.96f * unit(fall, 1)

    fun length(fall: Int): Float = 70f + 50f * unit(fall, 2)

    fun startAngle(fall: Int): Float = -60f + 120f * unit(fall, 3)

    fun endAngle(fall: Int): Float = -240f + 480f * unit(fall, 4)

    private fun unit(fall: Int, salt: Long): Float {
        var value = id * 1_000_003L + fall * 7_919L + salt + -0x61c8864680b583ebL
        value = (value xor (value ushr 30)) * -0x40a7b892e31b1a47L
        value = (value xor (value ushr 27)) * -0x6b2fb644ecceee15L
        value = value xor (value ushr 31)
        return (value ushr 11).toDouble().div(1L shl 53).toFloat()
    }

    companion object {

        fun makeDrops(duration: Double, count: Int, random: Random = Random.Default): List<BaguetteDrop> = (0 until count).map { index ->
            BaguetteDrop(
                id = index,
                delay = index.toDouble() / count * duration,
                fallDuration = random.nextDouble(1.1, 1.6),
                cycle = duration,
            )
        }
    }
}

@Composable
fun BaguetteRain(modifier: Modifier = Modifier, duration: Double = 5.0, count: Int = 40) {
    val drops = remember { BaguetteDrop.makeDrops(duration, count) }
    val palette = rememberIllustrationPalette()
    val elapsed by rememberElapsedSeconds()
    Canvas(modifier.fillMaxSize()) {
        scale(density, density, Offset.Zero) {
            val width = size.width / density
            val height = size.height / density
            drops.forEach { drop ->
                val sinceDelay = elapsed - drop.delay
                if (sinceDelay < 0) return@forEach
                val fall = (sinceDelay / drop.cycle).toInt()
                val progress = ((sinceDelay - fall * drop.cycle) / drop.fallDuration).toFloat()
                if (progress <= 1f) {
                    val length = drop.length(fall)
                    val startAngle = drop.startAngle(fall)
                    group(
                        offsetX = width * drop.xFraction(fall),
                        offsetY = -length + (height + 2 * length) * progress,
                        rotation = startAngle + (drop.endAngle(fall) - startAngle) * progress,
                    ) { drawBaguette(length, palette) }
                }
            }
        }
    }
}

internal fun DrawScope.drawBaguette(length: Float, palette: IllustrationPalette) {
    val thickness = length * 0.22f
    capsule(palette.baguetteCrust, 0f, 0f, length, thickness)
    val scoreWidth = length * 0.14f
    val spacing = length * 0.07f
    val totalWidth = 4 * scoreWidth + 3 * spacing
    for (index in 0 until 4) {
        val centerX = -totalWidth / 2 + scoreWidth / 2 + index * (scoreWidth + spacing)
        capsule(palette.baguetteScore, centerX, 0f, scoreWidth, thickness * 0.2f, rotation = -55f)
    }
}
