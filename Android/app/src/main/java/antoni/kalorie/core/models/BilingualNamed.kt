package antoni.kalorie.core.models

import java.util.Locale
import antoni.kalorie.textkit.displayName as resolveDisplayName

interface BilingualNamed {
    val czName: String
    val skName: String
    val engName: String
}

val currentLanguageCode: String
    get() = Locale.getDefault().language

val isCzechOrSlovakLocale: Boolean
    get() = currentLanguageCode in listOf("cs", "sk")

val BilingualNamed.displayName: String
    get() = displayName(currentLanguageCode)

fun BilingualNamed.displayName(languageCode: String): String = resolveDisplayName(languageCode = languageCode, czech = czName, slovak = skName, english = engName)
