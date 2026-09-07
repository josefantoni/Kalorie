fun searchTerms(input: String): List<String> =
    foldDiacritics(input.lowercase())
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .flatMap { word -> (1..word.length).map { length -> word.take(length) } }
        .distinct()
