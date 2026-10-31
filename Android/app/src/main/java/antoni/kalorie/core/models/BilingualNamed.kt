package antoni.kalorie.core.models

import java.util.Locale

interface BilingualNamed {
    val czName: String
    val engName: String
}

val currentLanguageCode: String
    get() = Locale.getDefault().language

val isCzechOrSlovakLocale: Boolean
    get() = currentLanguageCode in listOf("cs", "sk")

val BilingualNamed.displayName: String
    get() {
        if (isCzechOrSlovakLocale) return czName.ifEmpty { engName }
        return engName.ifEmpty { czName }
    }
