package antoni.kalorie.textkit

fun searchTerms(input: String): List<String> =
    foldDiacritics(input.lowercase())
        .split(Regex(" +"))
        .filter { it.isNotEmpty() }
        .flatMap { word -> (1..word.length).map { length -> word.take(length) } }
        .distinct()

data class SearchQuery(val lowercased: String, val folded: String, val lastWord: String)

fun searchQuery(query: String): SearchQuery {
    val lowercased = query.lowercase()
    val folded = foldDiacritics(lowercased)
    val lastWord = folded.split(" ").lastOrNull { it.isNotEmpty() } ?: folded
    return SearchQuery(lowercased = lowercased, folded = folded, lastWord = lastWord)
}
