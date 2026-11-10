package antoni.kalorie.components

import android.graphics.BlurMaskFilter
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import antoni.kalorie.R
import antoni.kalorie.macrokit.energyShares
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Paint as NativePaint

val GOAL_RING_SIZE = 72.dp

private const val MAX_LAPS = 3.0
private const val FULL_CIRCLE = 360.0
private const val START_ANGLE = -90.0
private const val SEAM_OVERLAP = 0.004
private const val TRACK_ALPHA = 0.2f
private const val LAP_SHADOW_ALPHA = 0.6f
private val LINE_WIDTH = 8.dp
private val LAP_SHADOW_BLUR = 3.dp

private data class RingSegment(val start: Double, val end: Double, val color: Color)

private class LapShadowPaints(blurRadius: Float, lineWidth: Float) {
    private val blur = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    private val shadowColor = Color.Black.copy(alpha = LAP_SHADOW_ALPHA).toArgb()
    val arc = NativePaint().apply {
        isAntiAlias = true
        color = shadowColor
        style = NativePaint.Style.STROKE
        strokeWidth = lineWidth
        maskFilter = blur
    }
    val head = NativePaint().apply {
        isAntiAlias = true
        color = shadowColor
        style = NativePaint.Style.FILL
        maskFilter = blur
    }
}

@Composable
fun GoalRingView(
    progress: Double?,
    modifier: Modifier = Modifier,
    protein: Double = 0.0,
    carbs: Double = 0.0,
    fat: Double = 0.0,
) {
    // MARK: - Properties

    val proteinColor = AppColors.protein
    val carbsColor = AppColors.carbs
    val fatColor = AppColors.fat
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
    val percentFormat = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 } }
    val density = LocalDensity.current
    val shadowPaints = remember(density) {
        with(density) { LapShadowPaints(LAP_SHADOW_BLUR.toPx(), LINE_WIDTH.toPx()) }
    }

    // MARK: - Body

    Box(
        modifier = modifier.size(GOAL_RING_SIZE).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(GOAL_RING_SIZE)) {
            drawRing(progress, protein, carbs, fat, proteinColor, carbsColor, fatColor, neutralColor, shadowPaints)
        }
        if (progress != null) {
            Text(
                text = percentFormat.format(progress),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = LINE_WIDTH + 4.dp),
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            )
        } else {
            Text(
                text = stringResource(R.string.dashboard_goal_setGoal),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = LINE_WIDTH + 4.dp),
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            )
        }
    }
}

// MARK: - Functions

private fun DrawScope.drawRing(
    progress: Double?,
    protein: Double,
    carbs: Double,
    fat: Double,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
    neutralColor: Color,
    shadowPaints: LapShadowPaints,
) {
    val lineWidth = LINE_WIDTH.toPx()
    val center = Offset(size.width / 2, size.height / 2)
    val radius = min(size.width, size.height) / 2 - lineWidth / 2 - 2.dp.toPx()
    drawCircle(neutralColor.copy(alpha = TRACK_ALPHA), radius = radius, center = center, style = Stroke(width = lineWidth))

    if (progress == null || progress <= 0) return
    val laps = min(progress, MAX_LAPS)
    val segments = colourSegments(min(laps, 1.0), protein, carbs, fat, proteinColor, carbsColor, fatColor, neutralColor)
    val lapCount = Math.ceil(laps).toInt()
    for (lap in 0 until lapCount) {
        val fill = min(1.0, laps - lap)
        if (lap > 0) drawLapShadow(fill, center, radius, lineWidth, shadowPaints)
        drawLap(fill, lap == 0, segments, center, radius, lineWidth)
    }
}

private fun colourSegments(
    patternLength: Double,
    protein: Double,
    carbs: Double,
    fat: Double,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
    neutralColor: Color,
): List<RingSegment> {
    val shares = energyShares(protein, carbs, fat)
    val total = shares.sum()
    if (total <= 0) return listOf(RingSegment(0.0, patternLength, neutralColor))
    val ordered = listOf(shares[2] to fatColor, shares[1] to carbsColor, shares[0] to proteinColor)
    val segments = mutableListOf<RingSegment>()
    var cursor = 0.0
    for ((share, color) in ordered) {
        if (share <= 0) continue
        val end = cursor + patternLength * share / total
        segments += RingSegment(cursor, end, color)
        cursor = end
    }
    return segments
}

private fun DrawScope.drawLapShadow(fill: Double, center: Offset, radius: Float, lineWidth: Float, paints: LapShadowPaints) {
    val headRadians = Math.toRadians(START_ANGLE + FULL_CIRCLE * fill)
    val headX = center.x + radius * cos(headRadians).toFloat()
    val headY = center.y + radius * sin(headRadians).toFloat()
    val bounds = RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawArc(bounds, START_ANGLE.toFloat(), (FULL_CIRCLE * fill).toFloat(), false, paints.arc)
        canvas.nativeCanvas.drawCircle(headX, headY, lineWidth / 2, paints.head)
    }
}

private fun DrawScope.drawLap(fill: Double, isFirst: Boolean, segments: List<RingSegment>, center: Offset, radius: Float, lineWidth: Float) {
    val first = segments.firstOrNull()
    if (isFirst && first != null) drawCap(START_ANGLE, first.color, center, radius, lineWidth)
    var headColor: Color? = null
    for (segment in segments) {
        val to = min(segment.end, fill)
        if (to <= segment.start) continue
        val overlappedTo = if (to < fill) min(to + SEAM_OVERLAP, fill) else to
        drawArc(
            color = segment.color,
            startAngle = (START_ANGLE + FULL_CIRCLE * segment.start).toFloat(),
            sweepAngle = (FULL_CIRCLE * (overlappedTo - segment.start)).toFloat(),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = lineWidth, cap = StrokeCap.Butt),
        )
        headColor = segment.color
    }
    headColor?.let { drawCap(START_ANGLE + FULL_CIRCLE * fill, it, center, radius, lineWidth) }
}

private fun DrawScope.drawCap(angle: Double, color: Color, center: Offset, radius: Float, lineWidth: Float) {
    val radians = Math.toRadians(angle)
    val point = Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
    drawCircle(color, radius = lineWidth / 2, center = point)
}
