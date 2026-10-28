package antoni.kalorie.features.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import antoni.kalorie.core.models.BodyWeightUnit
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MINIMUM_SPAN_KILOGRAMS = 2.0
private const val MINIMUM_LABEL_SPACING_DP = 14f
private val DOT_RADIUS = 3.dp
private val VERTICAL_INSET = 7.dp
private val CHART_HEIGHT = 160.dp
private val AXIS_WIDTH = 36.dp

class BodyWeightChartGeometry(
    private val points: List<BodyWeightChartPoint>,
    private val period: BodyWeightPeriod,
    private val unit: BodyWeightUnit,
) {

    // MARK: - Properties

    val yRange: ClosedFloatingPointRange<Double> by lazy {
        val values = points.map { unit.display(it.kilograms) }
        val low = values.minOrNull()
        val high = values.maxOrNull()
        if (low == null || high == null) {
            0.0..1.0
        } else {
            val minimumSpan = unit.display(MINIMUM_SPAN_KILOGRAMS) - unit.display(0.0)
            val span = maxOf(high - low, minimumSpan)
            val middle = (high + low) / 2
            (middle - span / 2)..(middle + span / 2)
        }
    }

    // MARK: - Functions

    fun position(point: BodyWeightChartPoint, width: Float, height: Float, dotRadius: Float, verticalInset: Float): Offset {
        val duration = maxOf(period.durationSeconds, 1L)
        val fractionX = (point.date.epochSecond - period.start.epochSecond).toDouble() / duration
        val plotWidth = width - 2 * dotRadius
        return Offset(
            x = (dotRadius + plotWidth * fractionX).toFloat(),
            y = yPosition(unit.display(point.kilograms), height, verticalInset),
        )
    }

    fun yPosition(displayValue: Double, height: Float, verticalInset: Float): Float {
        val range = yRange
        val fractionY = (displayValue - range.start) / (range.endInclusive - range.start)
        return (verticalInset + (height - 2 * verticalInset) * (1 - fractionY)).toFloat()
    }

    fun labelledValues(height: Float, verticalInset: Float, minimumSpacing: Float): List<Pair<Double, Float>> {
        val values = points.map { unit.display(it.kilograms) }
        if (values.isEmpty()) return emptyList()
        val highest = values.indices.maxByOrNull { values[it] } ?: return emptyList()
        val lowest = values.indices.minByOrNull { values[it] } ?: return emptyList()
        val labelled = mutableListOf<Pair<Double, Float>>()
        val visited = mutableSetOf<Int>()
        for (index in listOf(highest, lowest, values.lastIndex, 0) + values.indices) {
            if (!visited.add(index)) continue
            val offsetY = yPosition(values[index], height, verticalInset)
            if (labelled.any { kotlin.math.abs(it.second - offsetY) < minimumSpacing }) continue
            labelled += values[index] to offsetY
        }
        return labelled
    }
}

@Composable
fun BodyWeightChartView(
    points: List<BodyWeightChartPoint>,
    period: BodyWeightPeriod,
    unit: BodyWeightUnit,
    modifier: Modifier = Modifier,
) {
    // MARK: - Properties

    val geometry = remember(points, period, unit) { BodyWeightChartGeometry(points, period, unit) }
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.outline
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, fontSize = 10.sp)
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val zone = remember { ZoneId.systemDefault() }
    val valueFormat = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }

    // MARK: - Body

    Column(modifier = modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(modifier = Modifier.width(AXIS_WIDTH).height(CHART_HEIGHT)) {
                drawAxisLabels(geometry, textMeasurer, labelStyle, valueFormat)
            }
            Canvas(modifier = Modifier.weight(1f).height(CHART_HEIGHT)) {
                drawPlot(geometry, points, lineColor, guideColor)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = period.start.atZone(zone).format(dateFormatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = period.end.minusSeconds(1).atZone(zone).format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// MARK: - Functions

private fun DrawScope.drawPlot(
    geometry: BodyWeightChartGeometry,
    points: List<BodyWeightChartPoint>,
    lineColor: androidx.compose.ui.graphics.Color,
    guideColor: androidx.compose.ui.graphics.Color,
) {
    val dotRadius = DOT_RADIUS.toPx()
    val inset = VERTICAL_INSET.toPx()
    for ((_, offsetY) in geometry.labelledValues(size.height, inset, MINIMUM_LABEL_SPACING_DP.dp.toPx())) {
        drawLine(guideColor.copy(alpha = 0.15f), Offset(0f, offsetY), Offset(size.width, offsetY), strokeWidth = 0.5.dp.toPx())
    }
    drawLine(guideColor.copy(alpha = 0.3f), Offset(0f, size.height - inset), Offset(size.width, size.height - inset), strokeWidth = 1.dp.toPx())

    val positions = points.map { geometry.position(it, size.width, size.height, dotRadius, inset) }
    val line = Path()
    positions.forEachIndexed { index, position ->
        if (index == 0) line.moveTo(position.x, position.y) else line.lineTo(position.x, position.y)
    }
    drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    positions.forEach { drawCircle(lineColor, radius = dotRadius, center = it) }
}

private fun DrawScope.drawAxisLabels(
    geometry: BodyWeightChartGeometry,
    textMeasurer: TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    valueFormat: NumberFormat,
) {
    for ((value, offsetY) in geometry.labelledValues(size.height, VERTICAL_INSET.toPx(), MINIMUM_LABEL_SPACING_DP.dp.toPx())) {
        val layout = textMeasurer.measure(valueFormat.format(value), style)
        drawText(layout, topLeft = Offset(size.width - layout.size.width, offsetY - layout.size.height / 2f))
    }
}
