package antoni.kalorie.core.models

import antoni.kalorie.core.utils.Constants
import java.time.Instant

data class UserNotificationDomain(
    val kind: Kind,
    val id: String,
    val itemId: String,
    val createdAt: Instant,
    val status: TicketStatus = TicketStatus.PENDING,
    val response: String? = null,
    val resolvedAt: Instant? = null,
    val seenAt: Instant? = null,
    val reason: String? = null,
    val photoUrl: String? = null,
) {

    // MARK: - Types

    enum class Kind(val collection: String) {
        REPORT(Constants.Firestore.FOOD_ITEM_REPORTS),
        PHOTO_SUGGESTION(Constants.Firestore.PHOTO_SUGGESTIONS),
    }

    // MARK: - Properties

    val rowId: String
        get() = "${kind.collection}/$id"

    val lastEventAt: Instant
        get() = resolvedAt ?: createdAt

    val isUnread: Boolean
        get() = seenAt?.let { it.isBefore(lastEventAt) } ?: true
}
