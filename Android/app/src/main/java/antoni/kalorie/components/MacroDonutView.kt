package antoni.kalorie.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val ProteinColor = Color(0xFF007AFF)
val CarbsColor = Color(0xFFFF9500)
val FatColor = Color(0xFFFF2D55)

private const val INNER_RADIUS_RATIO = 0.58f
private const val MIN_LABELLED_PERCENT = 8

@Composable
fun MacroDonutView(
    protein: Double,
    carbs: Double,
    fat: Double,
    calories: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawDonut(textMeasurer, protein, carbs, fat)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawDonut(textMeasurer: TextMeasurer, protein: Double, carbs: Double, fat: Double) {
    val center = Offset(size.width / 2, size.height / 2)
    val outerRadius = min(size.width, size.height) / 2 - 2.dp.toPx()
    val innerRadius = outerRadius * INNER_RADIUS_RATIO
    val midRadius = (outerRadius + innerRadius) / 2
    val ringStyle = Stroke(width = outerRadius - innerRadius)
    val arcTopLeft = Offset(center.x - midRadius, center.y - midRadius)
    val arcSize = Size(midRadius * 2, midRadius * 2)
    val total = protein + carbs + fat

    if (total <= 0) {
        drawArc(Color.Gray.copy(alpha = 0.2f), 0f, 360f, false, arcTopLeft, arcSize, style = ringStyle)
        return
    }

    var startAngle = -90f
    for ((value, color) in listOf(protein to ProteinColor, carbs to CarbsColor, fat to FatColor)) {
        if (value <= 0) continue
        val sweep = (360 * value / total).toFloat()
        drawArc(color, startAngle, sweep, false, arcTopLeft, arcSize, style = ringStyle)

        val percent = (value / total * 100).roundToInt()
        if (percent >= MIN_LABELLED_PERCENT) {
            val midAngle = Math.toRadians((startAngle + sweep / 2).toDouble())
            val label = textMeasurer.measure(
                "$percent%",
                TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            val labelSize: IntSize = label.size
            drawText(
                label,
                topLeft = Offset(
                    center.x + midRadius * cos(midAngle).toFloat() - labelSize.width / 2,
                    center.y + midRadius * sin(midAngle).toFloat() - labelSize.height / 2,
                ),
            )
        }
        startAngle += sweep
    }
}
