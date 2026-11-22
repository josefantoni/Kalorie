package antoni.kalorie.features.dashboard

import java.time.Instant

class ReviewPromptStoreFake(
    override var loggingDays: Set<String> = emptySet(),
    override var dismissCount: Int = 0,
    override var lastDismissedAt: Instant? = null,
    override var isCompleted: Boolean = false,
) : ReviewPromptStoreProtocol
