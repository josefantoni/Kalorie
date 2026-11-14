package antoni.kalorie.features.notifications

import java.time.Instant

class UserNotificationsStoreFake(
    override var mayHaveUnread: Boolean = false,
    override var lastCheckedAt: Instant? = null,
    override var lastSubmittedAt: Instant? = null,
) : UserNotificationsStoreProtocol
