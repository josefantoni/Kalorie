package antoni.kalorie.core.models

import java.util.Locale

interface BilingualNamed {
    val czName: String
    val engName: String
}

val BilingualNamed.displayName: String
    get() {
        val isCzechOrSlovak = Locale.getDefault().language in listOf("cs", "sk")
        if (isCzechOrSlovak) return czName
        return engName.ifEmpty { czName }
    }
