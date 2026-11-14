package antoni.kalorie.core.networking

import antoni.kalorie.core.utils.epochSecondsAsDouble
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class NotificationSeenDTO(
    @SerialName("seen_at") val seenAt: Double,
) {

    // MARK: - Init

    constructor(seenAt: Instant) : this(seenAt = seenAt.epochSecondsAsDouble())
}
