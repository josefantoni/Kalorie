package antoni.kalorie.features.dashboard

import android.content.Context
import androidx.core.content.edit
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface SignInSpotlightStoreProtocol {
    var lastShownAt: Instant?
}

class SignInSpotlightStore(context: Context) : SignInSpotlightStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences(Constants.Auth.SIGN_IN_SPOTLIGHT_PREFERENCES, Context.MODE_PRIVATE)

    override var lastShownAt: Instant?
        get() = if (preferences.contains(Constants.Auth.SIGN_IN_SPOTLIGHT_LAST_SHOWN_AT_KEY)) Instant.ofEpochSecond(preferences.getLong(Constants.Auth.SIGN_IN_SPOTLIGHT_LAST_SHOWN_AT_KEY, 0)) else null
        set(value) {
            preferences.edit {
                if (value == null) remove(Constants.Auth.SIGN_IN_SPOTLIGHT_LAST_SHOWN_AT_KEY) else putLong(Constants.Auth.SIGN_IN_SPOTLIGHT_LAST_SHOWN_AT_KEY, value.epochSecond)
            }
        }
}
