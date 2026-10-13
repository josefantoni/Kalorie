package antoni.kalorie.core.models

import java.util.Locale

interface BilingualNamed {
    val czName: String
    val engName: String
}

val isCzechOrSlovakLocale: Boolean
    get() = Locale.getDefault().language in listOf("cs", "sk")

val BilingualNamed.displayName: String
    get() {
        if (isCzechOrSlovakLocale) return czName.ifEmpty { engName }
        return engName.ifEmpty { czName }
    }
