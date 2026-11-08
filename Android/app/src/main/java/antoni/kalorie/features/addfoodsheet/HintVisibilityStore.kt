package antoni.kalorie.features.addfoodsheet

import android.content.Context
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

private val HINT_HIDE_DAYS = listOf(3L, 5L, 8L, 13L, 21L, 30L)

interface HintVisibilityStoreProtocol {
    var hiddenAt: Instant?
    var hideCount: Int

    fun isHintVisible(now: Instant): Boolean {
        val hiddenAt = hiddenAt ?: return true
        val days = HINT_HIDE_DAYS[minOf(maxOf(hideCount, 1), HINT_HIDE_DAYS.size) - 1]
        return Duration.between(hiddenAt, now) >= Duration.ofDays(days)
    }
}

class HintVisibilityStore(
    context: Context,
    preferencesName: String,
    private val hiddenAtKey: String,
    private val hideCountKey: String,
) : HintVisibilityStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override var hiddenAt: Instant?
        get() = if (preferences.contains(hiddenAtKey)) Instant.ofEpochSecond(preferences.getLong(hiddenAtKey, 0)) else null
        set(value) {
            preferences.edit {
                if (value == null) remove(hiddenAtKey) else putLong(hiddenAtKey, value.epochSecond)
            }
        }

    override var hideCount: Int
        get() = preferences.getInt(hideCountKey, 0)
        set(value) {
            preferences.edit { putInt(hideCountKey, value) }
        }
}
