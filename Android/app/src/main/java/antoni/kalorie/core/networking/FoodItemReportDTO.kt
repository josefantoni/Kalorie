package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.TicketLocale
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FoodItemReportDTO(
    val id: String,
    val barcode: String,
    @SerialName("reported_by") val reportedBy: String? = null,
    val reason: String,
    @SerialName("reported_at") val reportedAt: Double,
    val status: TicketStatus,
    val locale: String? = null,
    val response: String? = null,
    @SerialName("resolved_at") val resolvedAt: Double? = null,
    @SerialName("seen_at") val seenAt: Double? = null,
) {

    // MARK: - Functions

    fun asDomain(): FoodItemReportDomain = FoodItemReportDomain(
        id = id,
        barcode = barcode,
        reportedBy = reportedBy,
        reason = reason,
        reportedAt = instantFromEpochSeconds(reportedAt),
        status = status,
        locale = TicketLocale.resolved(locale),
        response = response,
        resolvedAt = resolvedAt?.let { instantFromEpochSeconds(it) },
        seenAt = seenAt?.let { instantFromEpochSeconds(it) },
    )
}

@Serializable
data class FoodItemReportCreateDTO(
    val barcode: String,
    @SerialName("reported_by") val reportedBy: String,
    val reason: String,
    @SerialName("reported_at") val reportedAt: Double,
    val status: TicketStatus,
    val locale: String,
    val hidden: Boolean,
) {

    // MARK: - Init

    constructor(barcode: String, reportedBy: String, reason: String, reportedAt: Instant, locale: String) :
        this(
            barcode = barcode,
            reportedBy = reportedBy,
            reason = reason,
            reportedAt = reportedAt.epochSecondsAsDouble(),
            status = TicketStatus.PENDING,
            locale = locale,
            hidden = false,
        )
}
