package antoni.kalorie.components.illustrations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun LunchIllustration(modifier: Modifier = Modifier) {
    val palette = rememberIllustrationPalette()
    val time by rememberElapsedSeconds()
    IllustrationCanvas(320.dp, 200.dp, modifier) {
        oval(palette.ink.copy(alpha = 0.1f), 0f, 86f, 300f, 22f)
        group(112f, 28f) { popIn(time, 0.45) { drawLemonade(palette, time) } }
        group(-30f, 60f) { popIn(time, 0.0) { drawPlate(palette) } }
        oval(palette.ink.copy(alpha = 0.14f), -50f, 62f, 110f, 12f)
        group(-50f, 28f) { popIn(time, 0.15) { drawChicken(palette) } }
        for (index in 0 until 3) drawSteamPuff(palette, time, index)
        group(38f, 42f) { popIn(time, 0.3) { drawBread(palette) } }
    }
}

private fun DrawScope.popIn(time: Double, delay: Double, block: DrawScope.() -> Unit) {
    val shifted = (((time - delay) / 0.5).coerceIn(0.0, 1.0) - 1).toFloat()
    val scale = 1 + 2.70158f * shifted * shifted * shifted + 1.70158f * shifted * shifted
    scale(scale, Offset.Zero) { block() }
}

private fun DrawScope.drawSteamPuff(palette: IllustrationPalette, time: Double, index: Int) {
    val period = 2.4
    val phase = (((time + index * period / 3) % period) / period).toFloat()
    val fadeIn = ((time - 0.6) / 0.4).coerceIn(0.0, 1.0).toFloat()
    circle(
        palette.ink.copy(alpha = 0.16f * (1 - phase) * fadeIn),
        -50f + (index - 1) * 20f + sin(phase * PI.toFloat() * 2 + index) * 6f,
        18f - 56f * phase,
        10f + 16f * phase,
    )
}

private fun DrawScope.drawBubble(palette: IllustrationPalette, time: Double, index: Int) {
    val period = 1.6 + index * 0.3
    val phase = (((time + index * 0.45) % period) / period).toFloat()
    circle(
        palette.plate.copy(alpha = 0.7f * sin(phase * PI.toFloat())),
        index * 10f - 15f + sin(phase * PI.toFloat() * 2 + index) * 3f,
        32f - 64f * phase,
        5f + (index % 2) * 2f,
    )
}

private fun plateBowlPath(width: Float, height: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(width, 0f)
    cubicTo(width * 0.97f, height * 0.7f, width * 0.9f, height, width * 0.8f, height)
    lineTo(width * 0.2f, height)
    cubicTo(width * 0.1f, height, width * 0.03f, height * 0.7f, 0f, 0f)
    close()
}

private fun glassPath(width: Float, height: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(width, 0f)
    lineTo(width * 0.88f, height)
    quadraticTo(width / 2, height * 1.06f, width * 0.12f, height)
    close()
}

private fun DrawScope.drawPlate(palette: IllustrationPalette) {
    translate(-95f, -3f) {
        val bowl = plateBowlPath(190f, 22f)
        drawPath(bowl, palette.plate)
        drawPath(bowl, palette.ink.copy(alpha = 0.08f))
    }
    oval(palette.plate, 0f, 0f, 200f, 22f)
    oval(palette.ink.copy(alpha = 0.12f), 0f, 0f, 200f, 22f, style = Stroke(1.5f))
    oval(palette.ink.copy(alpha = 0.07f), 0f, 0f, 140f, 12f)
}

private fun DrawScope.drawLeg(color: Color, palette: IllustrationPalette) {
    group(rotation = -50f) {
        capsule(palette.bone, 30f, 0f, 30f, 8f)
        circle(palette.bone, 46f, -4f, 11f)
        circle(palette.bone, 46f, 4f, 11f)
        oval(color, 0f, 0f, 44f, 28f)
    }
}

private fun DrawScope.drawChicken(palette: IllustrationPalette) {
    group(30f, -12f) { drawLeg(palette.chickenShade, palette) }
    oval(palette.chickenRoast, 0f, 0f, 118f, 70f)
    val lower = Path().apply { addOval(Rect(-59f, -35f + 24f, 59f, 35f + 24f)) }
    clipPath(lower) { oval(palette.chickenShade.copy(alpha = 0.45f), 0f, 0f, 118f, 70f) }
    oval(palette.bone.copy(alpha = 0.32f), -14f, -18f, 56f, 18f, rotation = -14f)
    oval(palette.chickenShade, -42f, 2f, 26f, 16f, rotation = 24f)
    for (index in 0 until 4) {
        circle(palette.chickenShade.copy(alpha = 0.55f), index * 17f - 22f, (index % 2) * 14f - 4f, 5f)
    }
    group(22f, -8f) { drawLeg(palette.chickenRoast, palette) }
}

private fun DrawScope.drawBread(palette: IllustrationPalette) {
    oval(palette.ink.copy(alpha = 0.14f), 0f, 20f, 60f, 8f)
    oval(palette.baguetteCrust, 0f, 0f, 62f, 42f)
    oval(palette.breadCrumb.copy(alpha = 0.45f), -8f, -11f, 30f, 10f, rotation = -12f)
    for (index in 0 until 3) {
        val offset = index - 1
        capsule(
            palette.baguetteScore,
            offset * 11f,
            -2f + abs(offset) * 3f,
            4f,
            17f,
            rotation = offset * 38f,
        )
    }
}

private fun DrawScope.drawLemonade(palette: IllustrationPalette, time: Double) {
    capsule(palette.baguetteScore, 10f, -26f, 6f, 74f, rotation = 14f + 4f * sin(time * 2).toFloat())
    translate(-30f, -41f) {
        val glass = glassPath(60f, 82f)
        drawPath(glass, palette.lemonade)
        clipRect(0f, 0f, 60f, 14f) { drawPath(glass, palette.plate.copy(alpha = 0.25f)) }
    }
    roundedRect(palette.plate.copy(alpha = 0.55f), -10f, -12f, 20f, 20f, 4f, rotation = 14f)
    roundedRect(palette.plate.copy(alpha = 0.55f), 10f, 6f, 20f, 20f, 4f, rotation = -10f)
    for (index in 0 until 4) drawBubble(palette, time, index)
    translate(-30f, -41f) { drawPath(glassPath(60f, 82f), palette.plate, style = Stroke(2.5f)) }
    capsule(palette.plate.copy(alpha = 0.7f), -19f, 2f, 4f, 50f)
    group(30f, -38f) {
        circle(palette.lemonSlice, 0f, 0f, 30f)
        circle(palette.lemonade, 0f, 0f, 22f)
        for (index in 0 until 4) {
            capsule(palette.lemonSlice, 0f, 0f, 2f, 22f, rotation = index * 45f)
        }
    }
}
