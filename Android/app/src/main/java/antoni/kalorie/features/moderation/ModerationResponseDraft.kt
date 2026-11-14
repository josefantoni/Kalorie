package antoni.kalorie.features.moderation

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import antoni.kalorie.R
import antoni.kalorie.core.models.TicketStatus
import java.util.Locale

data class ModerationResponseDraft(
    val status: TicketStatus,
    val text: String,
    @StringRes val errorTitleRes: Int? = null,
) {

    // MARK: - Properties

    val isSendable: Boolean
        get() = text.trim().isNotEmpty()
}

interface ModerationResponseTextProviderProtocol {
    fun reportAccepted(locale: String): String
    fun photoAccepted(locale: String): String
}

class ModerationResponseTextProvider(
    private val context: Context,
) : ModerationResponseTextProviderProtocol {

    // MARK: - Functions

    override fun reportAccepted(locale: String): String = localized(R.string.moderation_response_report_accepted, locale)

    override fun photoAccepted(locale: String): String = localized(R.string.moderation_response_photo_accepted, locale)

    // MARK: - Private

    private fun localized(@StringRes resource: Int, locale: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(locale))
        return context.createConfigurationContext(configuration).getString(resource)
    }
}
