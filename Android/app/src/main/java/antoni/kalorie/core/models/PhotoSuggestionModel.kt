package antoni.kalorie.core.models

import java.time.Instant

data class PhotoSuggestionDomain(
    val id: String,
    val itemId: String,
    val suggestedBy: String?,
    val photoUrl: String,
    val suggestedAt: Instant,
    val status: TicketStatus,
    val locale: String,
    val response: String? = null,
    val resolvedAt: Instant? = null,
    val seenAt: Instant? = null,
)
