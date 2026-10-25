package antoni.kalorie.textkit

fun sanitizeDecimalInput(input: String): String {
    var seenSeparator = false
    return input.filter { char ->
        if (char == '.' || char == ',') {
            val isFirstSeparator = !seenSeparator
            seenSeparator = true
            isFirstSeparator
        } else {
            char in '0'..'9'
        }
    }
}

fun parseDecimalInput(input: String): Double? = input.replace(',', '.').toDoubleOrNull()
