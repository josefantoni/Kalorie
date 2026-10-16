package antoni.kalorie.components.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import kotlin.math.min

class IllustrationPalette(
    val ink: Color,
    val plate: Color,
    val chickenRoast: Color,
    val chickenShade: Color,
    val breadCrumb: Color,
    val lemonade: Color,
    val lemonSlice: Color,
    val bone: Color,
    val baguetteCrust: Color,
    val baguetteScore: Color,
    val grandmaSkin: Color,
    val grandmaHair: Color,
    val grandmaDress: Color,
    val grandmaEye: Color,
    val grandmaMouth: Color,
    val potMetal: Color,
    val potShade: Color,
    val heart: Color,
)

@Composable
fun rememberIllustrationPalette(): IllustrationPalette = IllustrationPalette(
    ink = MaterialTheme.colorScheme.onSurface,
    plate = colorResource(R.color.plate),
    chickenRoast = colorResource(R.color.chicken_roast),
    chickenShade = colorResource(R.color.chicken_shade),
    breadCrumb = colorResource(R.color.bread_crumb),
    lemonade = colorResource(R.color.lemonade),
    lemonSlice = colorResource(R.color.lemon_slice),
    bone = colorResource(R.color.bone),
    baguetteCrust = colorResource(R.color.baguette_crust),
    baguetteScore = colorResource(R.color.baguette_score),
    grandmaSkin = colorResource(R.color.grandma_skin),
    grandmaHair = colorResource(R.color.grandma_hair),
    grandmaDress = colorResource(R.color.grandma_dress),
    grandmaEye = colorResource(R.color.grandma_eye),
    grandmaMouth = colorResource(R.color.grandma_mouth),
    potMetal = colorResource(R.color.pot_metal),
    potShade = colorResource(R.color.pot_shade),
    heart = AppColors.error,
)

@Composable
internal fun IllustrationCanvas(width: Dp, height: Dp, modifier: Modifier = Modifier, content: DrawScope.() -> Unit) {
    Canvas(modifier.then(Modifier.size(width, height))) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val pixelsPerDp = density
        withTransform({
            translate(centerX, centerY)
            scale(pixelsPerDp, pixelsPerDp, Offset.Zero)
        }) { content() }
    }
}

@Composable
internal fun rememberElapsedSeconds(): State<Double> {
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                if (start < 0) start = nanos
                elapsed.doubleValue = (nanos - start) / NANOS_PER_SECOND
            }
        }
    }
    return elapsed
}

private const val NANOS_PER_SECOND = 1_000_000_000.0

internal inline fun DrawScope.group(
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    rotation: Float = 0f,
    pivot: Offset = Offset.Zero,
    block: DrawScope.() -> Unit,
) {
    translate(offsetX, offsetY) {
        rotate(rotation, pivot) { block() }
    }
}

internal fun DrawScope.oval(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    rotation: Float = 0f,
    style: DrawStyle = Fill,
) {
    rotate(rotation, Offset(centerX, centerY)) {
        drawOval(color, Offset(centerX - width / 2, centerY - height / 2), Size(width, height), style = style)
    }
}

internal fun DrawScope.circle(
    color: Color,
    centerX: Float,
    centerY: Float,
    diameter: Float,
    style: DrawStyle = Fill,
) {
    drawCircle(color, diameter / 2, Offset(centerX, centerY), style = style)
}

internal fun DrawScope.capsule(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    rotation: Float = 0f,
) {
    roundedRect(color, centerX, centerY, width, height, min(width, height) / 2, rotation)
}

internal fun DrawScope.roundedRect(
    color: Color,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    radius: Float,
    rotation: Float = 0f,
) {
    rotate(rotation, Offset(centerX, centerY)) {
        drawRoundRect(
            color,
            Offset(centerX - width / 2, centerY - height / 2),
            Size(width, height),
            CornerRadius(radius, radius),
        )
    }
}
