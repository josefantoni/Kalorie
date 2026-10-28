package antoni.kalorie.features.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.BodyWeightWindow
import kotlin.math.abs

val WEIGHT_CIRCLE_SIZE = 72.dp
private const val MINIMUM_SPAN_KILOGRAMS = 2.0

class WeightSparkline(
    private val points: List<Double>,
    private val centerIndex: Int,
    private val minimumSpan: Double,
) {

    // MARK: - Functions

    fun positions(width: Float, height: Float): List<Offset> {
        if (points.size < 2 || centerIndex !in points.indices) return emptyList()
        val center = points[centerIndex]
        val deviation = points.maxOf { abs(it - center) }
        val halfSpan = maxOf(deviation, minimumSpan / 2)
        val rightCount = points.size - 1 - centerIndex
        val step = width / 2 / maxOf(centerIndex, rightCount, 1)
        return points.mapIndexed { index, value ->
            Offset(
                x = width / 2 + step * (index - centerIndex),
                y = height / 2 - height / 2 * ((value - center) / halfSpan).toFloat(),
            )
        }
    }
}

@Composable
fun WeightCircleView(
    weights: List<BodyWeightDomain>,
    laterWeights: List<BodyWeightDomain>,
    unit: BodyWeightUnit,
    modifier: Modifier = Modifier,
) {
    // MARK: - Properties

    val points = remember(weights, laterWeights, unit) {
        BodyWeightWindow(earlier = weights, later = laterWeights).chronological.map { unit.display(it.kilograms) }
    }
    val latest = weights.firstOrNull()

    // MARK: - Body

    Box(
        modifier = modifier.size(WEIGHT_CIRCLE_SIZE).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (latest != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = unit.formatted(latest.kilograms),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
                if (points.size >= 2) {
                    Sparkline(points = points, centerIndex = weights.size - 1, unit = unit)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(10.dp)
                    .blur(3.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

// MARK: - Functions

@Composable
private fun Sparkline(points: List<Double>, centerIndex: Int, unit: BodyWeightUnit) {
    val sparkline = remember(points, centerIndex, unit) {
        WeightSparkline(points, centerIndex, minimumSpan = unit.display(MINIMUM_SPAN_KILOGRAMS) - unit.display(0.0))
    }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.width(56.dp).height(16.dp)) {
        val positions = sparkline.positions(size.width, size.height)
        val path = Path()
        positions.forEachIndexed { index, position ->
            if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(dotColor, radius = 3.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
    }
}
