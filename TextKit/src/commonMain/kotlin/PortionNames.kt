package antoni.kalorie.textkit

private class PortionEntry(val cs: String, val sk: String, val en: String, val matches: List<String>)

private val portionEntries = listOf(
    PortionEntry("1 porce", "1 porcia", "1 serving", listOf("porce", "porcia", "serving", "portion")),
    PortionEntry("½ porce", "½ porcie", "½ serving", listOf("½ porce", "½ porcie", "½ serving", "polovicni porce", "polovicna porcia", "half serving", "half portion")),
    PortionEntry("1 lžíce", "1 lyžica", "1 tbsp", listOf("lzice", "lyzica", "tbsp", "tablespoon", "spoon")),
    PortionEntry("1 lžička", "1 lyžička", "1 tsp", listOf("lzicka", "lyzicka", "tsp", "teaspoon")),
    PortionEntry("1 kus", "1 kus", "1 piece", listOf("kus", "ks", "piece")),
    PortionEntry("1 větší kus", "1 väčší kus", "1 larger piece", listOf("vetsi kus", "vacsi kus", "larger piece")),
    PortionEntry("1 balení", "1 balenie", "1 package", listOf("baleni", "balenie", "package", "pack")),
    PortionEntry("1 vanička", "1 vanička", "1 tub", listOf("vanicka", "tub")),
    PortionEntry("1 vejce", "1 vajce", "1 egg", listOf("vejce", "vajce", "egg")),
    PortionEntry("1 sklenice", "1 pohár", "1 glass", listOf("sklenice", "glass")),
    PortionEntry("1 plechovka", "1 plechovka", "1 can", listOf("plechovka", "can")),
)

private val sizeTokens = listOf("s", "m", "l", "xl")
private val whitespaceRun = Regex("\\s+")
private val portionKeyFoldMap = diacriticFoldMap + mapOf('ä' to 'a', 'ô' to 'o', 'ľ' to 'l', 'ĺ' to 'l', 'ŕ' to 'r')

private fun portionKey(name: String): String =
    name.lowercase().map { portionKeyFoldMap[it] ?: it }.joinToString("").removePrefix("1 ")

fun localizedPortionName(name: String, languageCode: String): String {
    val collapsed = name.trim().replace(whitespaceRun, " ")
    val key = portionKey(collapsed)

    fun find(candidate: String) = portionEntries.firstOrNull { candidate in it.matches }
    val language = languageCode.lowercase().substringBefore('-').substringBefore('_')
    fun form(entry: PortionEntry) = when (language) {
        "cs" -> entry.cs
        "sk" -> entry.sk
        else -> entry.en
    }

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
