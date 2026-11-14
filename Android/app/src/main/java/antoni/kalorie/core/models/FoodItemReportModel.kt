package antoni.kalorie.core.models

import java.time.Instant

data class FoodItemReportDomain(
    val id: String,
    val barcode: String,
    val reportedBy: String?,
    val reason: String,
    val reportedAt: Instant,
    val status: TicketStatus,
    val locale: String,
    val response: String? = null,
    val resolvedAt: Instant? = null,
    val seenAt: Instant? = null,
)

sealed class FoodItemReportError : Exception() {
    data object ReasonRequired : FoodItemReportError()
    data object ReasonTooLong : FoodItemReportError()
}
