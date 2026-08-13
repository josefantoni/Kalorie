private val htmlEntities = listOf(
    "&amp;" to "&",
    "&quot;" to "\"",
    "&lt;" to "<",
    "&gt;" to ">",
    "&apos;" to "'",
    "&#39;" to "'",
    "&nbsp;" to " ",
)

fun decodeHtmlEntities(input: String): String =
    htmlEntities.fold(input) { acc, (entity, replacement) -> acc.replace(entity, replacement) }
