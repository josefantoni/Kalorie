package antoni.kalorie.core.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure
import java.text.NumberFormat
import java.util.Locale

fun Double.formatted(fractionDigits: Int, unitSymbol: String): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return "${formatter.format(this)} $unitSymbol"
}

fun Double.formattedTrimmed(): String = String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.')

@Composable
fun Double.formattedGrams(fractionDigits: Int = 1): String =
    formatted(fractionDigits, stringResource(R.string.common_unit_grams))

@Composable
fun Double.formattedAmount(measure: FoodMeasure, fractionDigits: Int = 1): String =
    formatted(fractionDigits, stringResource(measure.unitSymbolRes))

@Composable
fun Double?.formattedGrams(fractionDigits: Int = 1): String =
    if (this == null) "–" else formattedGrams(fractionDigits)
