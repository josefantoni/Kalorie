package antoni.kalorie.features.dashboard

import android.os.Build
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import antoni.kalorie.R
import antoni.kalorie.core.models.BodyWeightUnit
import java.util.Locale

@Composable
fun WeightPickerView(
    kilograms: Double,
    unit: BodyWeightUnit,
    onKilogramsChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    // MARK: - Properties

    val tenths = unit.tenths(kilograms)
    val currentTenths by rememberUpdatedState(tenths)
    val currentOnChange by rememberUpdatedState(onKilogramsChange)
    val wholeDescription = stringResource(R.string.dashboard_weight_pickerWhole)
    val tenthsDescription = stringResource(R.string.dashboard_weight_pickerTenths)
    val decimalSeparator = remember { java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator.toString() }
    val range = unit.rangeInTenths
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    fun update(newTenths: Int): Boolean {
        val clamped = newTenths.coerceIn(range.first, range.last)
        if (clamped == currentTenths) return false
        currentOnChange(unit.kilograms(clamped / 10.0))
        return true
    }

    // MARK: - Body

    Row(
        modifier = modifier.height(PICKER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AndroidView(
            modifier = Modifier.width(100.dp),
            factory = { context ->
                NumberPicker(ContextThemeWrapper(context, R.style.WeightPickerTheme)).apply {
                    minValue = range.first / 10
                    maxValue = range.last / 10
                    wrapSelectorWheel = false
                    contentDescription = wholeDescription
                    setOnValueChangedListener { picker, _, newValue ->
                        if (!update(newValue * 10 + currentTenths % 10)) {
                            picker.value = (currentTenths / 10).coerceIn(picker.minValue, picker.maxValue)
                        }
                    }
                }
            },
            update = { picker ->
                picker.isEnabled = isEnabled
                picker.applyTextColor(textColor)
                picker.value = (tenths / 10).coerceIn(picker.minValue, picker.maxValue)
            },
        )
        Text(text = decimalSeparator, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AndroidView(
            modifier = Modifier.width(60.dp),
            factory = { context ->
                NumberPicker(ContextThemeWrapper(context, R.style.WeightPickerTheme)).apply {
                    minValue = 0
                    maxValue = 9
                    wrapSelectorWheel = false
                    contentDescription = tenthsDescription
                    setOnValueChangedListener { picker, _, newValue ->
                        if (!update(currentTenths / 10 * 10 + newValue)) picker.value = currentTenths % 10
                    }
                }
            },
            update = { picker ->
                picker.isEnabled = isEnabled
                picker.applyTextColor(textColor)
                picker.value = tenths % 10
            },
        )
        Text(
            text = unit.symbol,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
    }
}

private val PICKER_HEIGHT = 216.dp

private fun NumberPicker.applyTextColor(color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        textColor = color
        return
    }
    for (index in 0 until childCount) {
        (getChildAt(index) as? EditText)?.setTextColor(color)
    }
    runCatching {
        val paint = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint").apply { isAccessible = true }.get(this) as android.graphics.Paint
        paint.color = color
        invalidate()
    }
}
