package antoni.kalorie.textkit

internal val diacriticFoldMap = mapOf(
    'á' to 'a', 'Á' to 'A',
    'č' to 'c', 'Č' to 'C',
    'ď' to 'd', 'Ď' to 'D',
    'é' to 'e', 'É' to 'E',
    'ě' to 'e', 'Ě' to 'E',
    'í' to 'i', 'Í' to 'I',
    'ň' to 'n', 'Ň' to 'N',
    'ó' to 'o', 'Ó' to 'O',
    'ř' to 'r', 'Ř' to 'R',
    'š' to 's', 'Š' to 'S',
    'ť' to 't', 'Ť' to 'T',
    'ú' to 'u', 'Ú' to 'U',
    'ů' to 'u', 'Ů' to 'U',
    'ý' to 'y', 'Ý' to 'Y',
    'ž' to 'z', 'Ž' to 'Z',
    'ä' to 'a', 'Ä' to 'A',
    'ô' to 'o', 'Ô' to 'O',
    'ĺ' to 'l', 'Ĺ' to 'L',
    'ľ' to 'l', 'Ľ' to 'L',
    'ŕ' to 'r', 'Ŕ' to 'R',
)

fun foldDiacritics(input: String): String =
    input.map { diacriticFoldMap[it] ?: it }.joinToString("")
