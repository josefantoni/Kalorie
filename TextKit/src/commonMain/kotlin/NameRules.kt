package antoni.kalorie.textkit

data class FoodNames(val czech: String, val slovak: String, val english: String)

fun slovakName(slovak: String?, czech: String?, english: String?): String =
    listOf(slovak, czech, english).firstOrNull { !it.isNullOrBlank() } ?: ""

fun nameLanguage(languageCode: String): String =
    when (languageCode) {
        "cs" -> "cs"
        "sk" -> "sk"
        else -> "en"
    }

fun originalName(nameLanguage: String, czech: String, slovak: String, english: String): String =
    when (nameLanguage) {
        "cs" -> czech
        "sk" -> slovak
        else -> english
    }.trim()

fun namesInLanguage(nameLanguage: String, name: String): FoodNames {
    val trimmed = name.trim()
    return when (nameLanguage) {
        "cs" -> FoodNames(czech = trimmed, slovak = "", english = "")
        "sk" -> FoodNames(czech = "", slovak = trimmed, english = "")
        else -> FoodNames(czech = "", slovak = "", english = trimmed)
    }
}

fun completeNames(nameLanguage: String, czech: String, slovak: String, english: String): FoodNames {
    val original = originalName(nameLanguage, czech, slovak, english)
    fun filled(name: String) = name.trim().ifEmpty { original }
    return FoodNames(czech = filled(czech), slovak = filled(slovak), english = filled(english))
}

fun displayName(languageCode: String, czech: String, slovak: String, english: String): String {
    val order = when (languageCode) {
        "cs" -> listOf(czech, slovak, english)
        "sk" -> listOf(slovak, czech, english)
        else -> listOf(english, czech, slovak)
    }
    return order.firstOrNull { it.isNotBlank() } ?: ""
}
