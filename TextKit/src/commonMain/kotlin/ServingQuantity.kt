package antoni.kalorie.textkit

import kotlin.math.floor

private const val WHITESPACE = " \t\n\r"
private const val MAX_SERVING_QUANTITY = 5000.0
private val PLAIN_DECIMAL = Regex("[0-9]+(\\.[0-9]+)?")

fun servingQuantity(number: Double?, text: String?): Double? {
    val value = number ?: text
        ?.trim { it in WHITESPACE }
        ?.takeIf(PLAIN_DECIMAL::matches)
        ?.toDoubleOrNull()
        ?: return null
    val scaled = value * 10
    if (!scaled.isFinite()) return null
    val rounded = floor(scaled + 0.5) / 10
    return rounded.takeIf { it in 0.0..MAX_SERVING_QUANTITY }
}

fun servingQuantityUnit(raw: String?): String? = raw?.trim { it in WHITESPACE }?.lowercase()
