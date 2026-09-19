fun formatNumber(value: Double, fractionDigits: Int, decimalSeparator: String): String {
    var scale = 1L
    repeat(fractionDigits) { scale *= 10 }
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (fractionDigits == 0) return "$sign$scaled"
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(fractionDigits, '0')
    return "$sign$whole$decimalSeparator$fraction"
}
