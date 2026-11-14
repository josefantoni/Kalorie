package antoni.kalorie.features.notifications

import android.content.Context
import androidx.core.content.edit
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface UserNotificationsStoreProtocol {
    var mayHaveUnread: Boolean
    var lastCheckedAt: Instant?
    var lastSubmittedAt: Instant?
}

fun UserNotificationsStoreProtocol.recordFetch(notifications: List<UserNotificationDomain>, startedAt: Instant) {
    if (lastSubmittedAt?.let { it.isAfter(startedAt) && !it.isAfter(Instant.now()) } == true) return
    mayHaveUnread = notifications.any { it.isUnread }
    lastCheckedAt = Instant.now()
}

class UserNotificationsStore(
    context: Context,
    private val authProvider: AuthProviderProtocol,
) : UserNotificationsStoreProtocol {

    // MARK: - Properties

    private val preferences = context.getSharedPreferences(Constants.Notifications.USER_PREFERENCES, Context.MODE_PRIVATE)

    override var mayHaveUnread: Boolean
        get() = key("mayHaveUnread")?.let { preferences.getBoolean(it, false) } ?: false
        set(value) {
            val key = key("mayHaveUnread") ?: return
            preferences.edit { putBoolean(key, value) }
        }

    override var lastCheckedAt: Instant?
        get() = key("lastCheckedAt")?.takeIf { preferences.contains(it) }?.let { Instant.ofEpochSecond(preferences.getLong(it, 0)) }
        set(value) {
            val key = key("lastCheckedAt") ?: return
            preferences.edit {
                if (value == null) remove(key) else putLong(key, value.epochSecond)
            }
        }

    override var lastSubmittedAt: Instant?
        get() = key("lastSubmittedAt")?.takeIf { preferences.contains(it) }?.let { Instant.ofEpochMilli(preferences.getLong(it, 0)) }
        set(value) {
            val key = key("lastSubmittedAt") ?: return
            preferences.edit {
                if (value == null) remove(key) else putLong(key, value.toEpochMilli())
            }
        }

    // MARK: - Private

    private fun key(name: String): String? = authProvider.userId?.let { "$it.$name" }
}
