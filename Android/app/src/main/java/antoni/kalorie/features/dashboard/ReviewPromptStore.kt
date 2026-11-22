package antoni.kalorie.features.dashboard

import android.content.Context
import androidx.core.content.edit
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface ReviewPromptStoreProtocol {
    var loggingDays: Set<String>
    var dismissCount: Int
    var lastDismissedAt: Instant?
    var isCompleted: Boolean
}

class ReviewPromptStore(context: Context) : ReviewPromptStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences(Constants.ReviewPrompt.PREFERENCES, Context.MODE_PRIVATE)

    override var loggingDays: Set<String>
        get() = preferences.getStringSet(Constants.ReviewPrompt.LOGGING_DAYS_KEY, null)?.toSet() ?: emptySet()
        set(value) {
            preferences.edit { putStringSet(Constants.ReviewPrompt.LOGGING_DAYS_KEY, value) }
        }

    override var dismissCount: Int
        get() = preferences.getInt(Constants.ReviewPrompt.DISMISS_COUNT_KEY, 0)
        set(value) {
            preferences.edit { putInt(Constants.ReviewPrompt.DISMISS_COUNT_KEY, value) }
        }

    override var lastDismissedAt: Instant?
        get() = if (preferences.contains(Constants.ReviewPrompt.LAST_DISMISSED_AT_KEY)) Instant.ofEpochSecond(preferences.getLong(Constants.ReviewPrompt.LAST_DISMISSED_AT_KEY, 0)) else null
        set(value) {
            preferences.edit {
                if (value == null) remove(Constants.ReviewPrompt.LAST_DISMISSED_AT_KEY) else putLong(Constants.ReviewPrompt.LAST_DISMISSED_AT_KEY, value.epochSecond)
            }
        }

    override var isCompleted: Boolean
        get() = preferences.getBoolean(Constants.ReviewPrompt.IS_COMPLETED_KEY, false)
        set(value) {
            preferences.edit { putBoolean(Constants.ReviewPrompt.IS_COMPLETED_KEY, value) }
        }
}
