package antoni.kalorie.core.models

import antoni.kalorie.core.utils.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class TicketStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("accepted")
    ACCEPTED,

    @SerialName("rejected")
    REJECTED,
}

object TicketLocale {

    // MARK: - Functions

    fun current(language: String = Locale.getDefault().language): String = resolved(language.take(2))

    fun resolved(locale: String?): String = locale?.takeIf { it in Constants.Firestore.SUPPORTED_LOCALES } ?: Constants.Firestore.FALLBACK_LOCALE
}
