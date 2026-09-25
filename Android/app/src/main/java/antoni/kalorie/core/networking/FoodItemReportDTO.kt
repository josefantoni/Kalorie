package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodItemReportDTO(
    val barcode: String,
    @SerialName("reported_by") val reportedBy: String,
    val reason: String,
    @SerialName("reported_at") val reportedAt: Double,
) {

    // MARK: - Init

    constructor(barcode: String, reportedBy: String, reason: String, reportedAt: Instant) :
        this(barcode = barcode, reportedBy = reportedBy, reason = reason, reportedAt = reportedAt.epochSecondsAsDouble())

    // MARK: - Functions

    fun asDomain(): FoodItemReportDomain = FoodItemReportDomain(
        barcode = barcode,
        reportedBy = reportedBy,
        reason = reason,
        reportedAt = instantFromEpochSeconds(reportedAt),
    )
}
