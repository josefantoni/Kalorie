package antoni.kalorie.core.networking

import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.utils.epochSecondsAsDouble
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TicketResolutionDTO(
    val status: TicketStatus,
    val response: String,
    @SerialName("resolved_at") val resolvedAt: Double,
    val hidden: Boolean,
) {

    // MARK: - Init

    constructor(status: TicketStatus, response: String, resolvedAt: Instant) :
        this(status = status, response = response, resolvedAt = resolvedAt.epochSecondsAsDouble(), hidden = false)
}
