package antoni.kalorie.components.illustrations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private enum class Food { DUMPLING, SCHNITZEL, HEART }

private const val POT_MOUTH_Y = 40f

@Composable
fun GrandmaCooking(modifier: Modifier = Modifier) {
    val palette = rememberIllustrationPalette()
    val time by rememberElapsedSeconds()
    IllustrationCanvas(320.dp, 280.dp, modifier) {
        group(offsetY = -22f) { drawGrandma(palette, time) }
        for (index in 0 until 3) drawSteamPuff(palette, time, index)
        for (index in 0 until 6) drawPoppingFood(palette, time, index)
        group(offsetY = 62f) { drawPot(palette) }
    }
}

private fun DrawScope.drawGrandma(palette: IllustrationPalette, time: Double) {
    roundedRect(palette.grandmaDress, 0f, 54f, 124f, 120f, 40f)
    roundedRect(palette.plate, 0f, 70f, 70f, 70f, 16f)
    circle(palette.grandmaHair, 0f, -78f, 30f)
    group(offsetY = -42f) { drawHead(palette) }
    drawArm(palette, time)
}

private fun DrawScope.drawHead(palette: IllustrationPalette) {
    circle(palette.grandmaSkin, 0f, 0f, 72f)
    clipRect(-36f, -36f, 36f, -10f) { circle(palette.grandmaHair, 0f, 0f, 72f) }
    for (index in 0 until 2) {
        val eyeX = if (index == 0) -14f else 14f
        circle(palette.potShade, eyeX, -2f, 20f, style = Stroke(2.5f))
        circle(palette.grandmaEye, eyeX, -2f, 5f)
        circle(palette.heart.copy(alpha = 0.35f), eyeX * 1.5f, 12f, 11f)
    }
    capsule(palette.potShade, 0f, -2f, 8f, 2.5f)
    drawArc(
        color = palette.grandmaMouth,
        startAngle = 0.12f * 360f,
        sweepAngle = 0.26f * 360f,
        useCenter = false,
        topLeft = Offset(-12f, 6f - 12f),
        size = Size(24f, 24f),
        style = Stroke(2.5f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawArm(palette: IllustrationPalette, time: Double) {
    val spoonAngle = (sin(time * 4) * 14).toFloat()
    group(offsetX = 34f, offsetY = 66f, rotation = spoonAngle, pivot = Offset(0f, 39f)) {
        capsule(palette.baguetteScore, 0f, -34f, 8f, 78f)
        circle(palette.grandmaSkin, 0f, 0f, 18f)
    }
    capsule(palette.grandmaDress, 14f, 62f, 60f, 22f, rotation = -25f)
}

private fun DrawScope.drawPot(palette: IllustrationPalette) {
    capsule(palette.potShade, -88f, -18f, 30f, 12f)
    capsule(palette.potShade, 88f, -18f, 30f, 12f)
    roundedRect(palette.potMetal, 0f, 0f, 160f, 84f, 22f)
    capsule(palette.plate.copy(alpha = 0.35f), -56f, 4f, 8f, 46f)
    capsule(palette.potShade, 0f, -40f, 172f, 16f)
}

private fun DrawScope.drawSteamPuff(palette: IllustrationPalette, time: Double, index: Int) {
    val period = 2.4
    val phase = (((time + index * period / 3) % period) / period).toFloat()
    circle(
        palette.plate.copy(alpha = 0.85f * (1 - phase)),
        (index - 1) * 34f + sin(phase * PI.toFloat() * 2 + index) * 10f,
        POT_MOUTH_Y + 20f - 70f * phase,
        22f + 26f * phase,
    )
}

private fun DrawScope.drawPoppingFood(palette: IllustrationPalette, time: Double, index: Int) {
    val period = 1.8
    val shifted = (((time + index * period / 6) % period) / period).toFloat()
    val direction = if (index % 2 == 0) -1f else 1f
    val spread = 70f + (index % 3) * 26f
    val rise = 4 * shifted * (1 - shifted) * (60f + (index % 3) * 18f)
    val alpha = if (shifted < 0.05f) shifted * 20f else 1f
    group(
        offsetX = direction * spread * (shifted * 0.6f + 0.4f),
        offsetY = POT_MOUTH_Y + 30f - rise,
        rotation = shifted * 240f * direction,
    ) { drawFood(palette, Food.entries[index % Food.entries.size], alpha) }
}

private fun DrawScope.drawFood(palette: IllustrationPalette, food: Food, alpha: Float) {
    when (food) {
        Food.DUMPLING -> {
            oval(palette.breadCrumb.copy(alpha = alpha), 0f, 0f, 34f, 22f)
            capsule(palette.baguetteCrust.copy(alpha = 0.6f * alpha), 0f, 3f, 22f, 3f)
        }
        Food.SCHNITZEL -> {
            roundedRect(palette.baguetteCrust.copy(alpha = alpha), 0f, 0f, 40f, 26f, 10f)
            roundedRect(palette.chickenRoast.copy(alpha = 0.7f * alpha), 0f, 0f, 28f, 14f, 8f)
        }
        Food.HEART -> drawPath(heartPath(), palette.heart.copy(alpha = alpha))
    }
}

private fun heartPath(): Path = Path().apply {
    moveTo(0f, 11f)
    cubicTo(-16f, -1f, -9f, -13f, 0f, -5f)
    cubicTo(9f, -13f, 16f, -1f, 0f, 11f)
    close()
}
