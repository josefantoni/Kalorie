package antoni.kalorie.features.addfoodsheet

import android.content.Context
import androidx.core.content.edit
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface ContributionHintStoreProtocol {
    var hiddenAt: Instant?
    var hideCount: Int
}

class ContributionHintStore(context: Context) : ContributionHintStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences(Constants.AddFood.CONTRIBUTION_HINT_PREFERENCES, Context.MODE_PRIVATE)

    override var hiddenAt: Instant?
        get() = if (preferences.contains(Constants.AddFood.CONTRIBUTION_HINT_HIDDEN_AT_KEY)) Instant.ofEpochSecond(preferences.getLong(Constants.AddFood.CONTRIBUTION_HINT_HIDDEN_AT_KEY, 0)) else null
        set(value) {
            preferences.edit {
                if (value == null) remove(Constants.AddFood.CONTRIBUTION_HINT_HIDDEN_AT_KEY) else putLong(Constants.AddFood.CONTRIBUTION_HINT_HIDDEN_AT_KEY, value.epochSecond)
            }
        }

    override var hideCount: Int
        get() = preferences.getInt(Constants.AddFood.CONTRIBUTION_HINT_HIDE_COUNT_KEY, 0)
        set(value) {
            preferences.edit { putInt(Constants.AddFood.CONTRIBUTION_HINT_HIDE_COUNT_KEY, value) }
        }
}
