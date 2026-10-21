package antoni.kalorie.textkit

private const val EDGE_PUNCTUATION = "()[]{},;:.!?\"'„“”‚‘’«»"

internal fun searchWords(folded: String): List<String> =
    folded.split(" ").map { it.trim { char -> char in EDGE_PUNCTUATION } }.filter { it.isNotEmpty() }

fun searchTerms(input: String): List<String> =
    searchWords(foldDiacritics(input.lowercase()))
        .flatMap { word -> (1..word.length).map { length -> word.take(length) } }
        .distinct()

data class SearchQuery(val lowercased: String, val folded: String, val lastWord: String)

fun searchQuery(query: String): SearchQuery {
    val lowercased = query.lowercase()
    val folded = foldDiacritics(lowercased)
    val lastWord = searchWords(folded).lastOrNull() ?: folded
    return SearchQuery(lowercased = lowercased, folded = folded, lastWord = lastWord)
}

fun matchesSearchQuery(name: String, query: SearchQuery): Boolean =
    foldDiacritics(name.lowercase()).startsWith(query.folded) ||
        (query.lastWord.isNotEmpty() && searchWords(foldDiacritics(name.lowercase())).any { it.startsWith(query.lastWord) })
