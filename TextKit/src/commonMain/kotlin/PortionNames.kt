package antoni.kalorie.textkit

private class PortionEntry(val cs: String, val en: String, val matches: List<String>)

private val portionEntries = listOf(
    PortionEntry("1 porce", "1 serving", listOf("porce", "serving", "portion")),
    PortionEntry("½ porce", "½ serving", listOf("½ porce", "½ serving", "polovicni porce", "half serving", "half portion")),
    PortionEntry("1 lžíce", "1 tbsp", listOf("lzice", "tbsp", "tablespoon", "spoon")),
    PortionEntry("1 lžička", "1 tsp", listOf("lzicka", "tsp", "teaspoon")),
    PortionEntry("1 kus", "1 piece", listOf("kus", "ks", "piece")),
    PortionEntry("1 balení", "1 package", listOf("baleni", "package", "pack")),
    PortionEntry("1 vanička", "1 tub", listOf("vanicka", "tub")),
    PortionEntry("1 vejce", "1 egg", listOf("vejce", "egg")),
    PortionEntry("1 sklenice", "1 glass", listOf("sklenice", "glass")),
    PortionEntry("1 plechovka", "1 can", listOf("plechovka", "can")),
)

private val sizeTokens = listOf("s", "m", "l", "xl")
private val whitespaceRun = Regex("\\s+")

fun localizedPortionName(name: String, czech: Boolean): String {
    val collapsed = name.trim().replace(whitespaceRun, " ")
    val key = foldDiacritics(collapsed.lowercase()).removePrefix("1 ")

    fun find(candidate: String) = portionEntries.firstOrNull { candidate in it.matches }
    fun form(entry: PortionEntry) = if (czech) entry.cs else entry.en

    find(key)?.let { return form(it) }

    val separator = key.lastIndexOf(' ')
    if (separator > 0) {
        val size = key.substring(separator + 1)
        if (size in sizeTokens) {
            find(key.substring(0, separator))?.let { return "${form(it)} ${size.uppercase()}" }
        }
    }

    return collapsed.replaceFirstChar { it.uppercase() }
}
