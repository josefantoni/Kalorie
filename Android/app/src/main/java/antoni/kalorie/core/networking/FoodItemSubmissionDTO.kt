package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodItemSubmissionDTO(
    val id: String,
    val barcode: String? = null,
    @SerialName("submitted_by") val submittedBy: String,
    val status: FoodItemSubmissionStatus,
    @SerialName("submitted_at") val submittedAt: Double,
    @SerialName("reject_reason") val rejectReason: String? = null,
    val item: FoodItemDTO,
) {

    // MARK: - Init

    constructor(
        id: String,
        barcode: String?,
        submittedBy: String,
        status: FoodItemSubmissionStatus,
        submittedAt: Instant,
        rejectReason: String?,
        item: FoodItemDomain,
    ) : this(
        id = id,
        barcode = barcode,
        submittedBy = submittedBy,
        status = status,
        submittedAt = submittedAt.epochSecondsAsDouble(),
        rejectReason = rejectReason,
        item = FoodItemDTO(item),
    )

    // MARK: - Functions

    fun asDomain(): FoodItemSubmissionDomain = FoodItemSubmissionDomain(
        id = id,
        barcode = barcode,
        submittedBy = submittedBy,
        status = status,
        submittedAt = instantFromEpochSeconds(submittedAt),
        rejectReason = rejectReason,
        item = item.asDomain(),
    )
}
