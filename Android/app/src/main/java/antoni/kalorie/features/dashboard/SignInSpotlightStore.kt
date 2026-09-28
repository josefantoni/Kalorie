package antoni.kalorie.features.dashboard

import android.content.Context
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
            val editor = preferences.edit()
            if (value == null) editor.remove(KEY) else editor.putLong(KEY, value.epochSecond)
            editor.apply()
        }

    private companion object {
        const val KEY = "signInSpotlightLastShownAt"
    }
}
