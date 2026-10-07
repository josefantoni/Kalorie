package antoni.kalorie.features.dashboard

import android.content.Context
import androidx.core.content.edit
import java.time.Instant

interface SignInSpotlightStoreProtocol {
    var lastShownAt: Instant?
}

class SignInSpotlightStore(context: Context) : SignInSpotlightStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences("signInSpotlight", Context.MODE_PRIVATE)

    override var lastShownAt: Instant?
        get() = if (preferences.contains(KEY)) Instant.ofEpochSecond(preferences.getLong(KEY, 0)) else null
        set(value) {
            preferences.edit {
                if (value == null) remove(KEY) else putLong(KEY, value.epochSecond)
            }
        }

    private companion object {
        const val KEY = "signInSpotlightLastShownAt"
    }
}
