package antoni.kalorie.core.networking

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketLocale
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class PhotoSuggestionDTO(
    val id: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("suggested_by") val suggestedBy: String? = null,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("suggested_at") val suggestedAt: Double,
    val status: TicketStatus,
    val locale: String? = null,
    val response: String? = null,
    @SerialName("resolved_at") val resolvedAt: Double? = null,
    @SerialName("seen_at") val seenAt: Double? = null,
) {

    // MARK: - Functions

    fun asDomain(): PhotoSuggestionDomain = PhotoSuggestionDomain(
        id = id,
        itemId = itemId,
        suggestedBy = suggestedBy,
        photoUrl = photoUrl,
        suggestedAt = instantFromEpochSeconds(suggestedAt),
        status = status,
        locale = TicketLocale.resolved(locale),
        response = response,
        resolvedAt = resolvedAt?.let { instantFromEpochSeconds(it) },
        seenAt = seenAt?.let { instantFromEpochSeconds(it) },
    )
}

@Serializable
data class PhotoSuggestionCreateDTO(
    @SerialName("item_id") val itemId: String,
    @SerialName("suggested_by") val suggestedBy: String,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("suggested_at") val suggestedAt: Double,
    val status: TicketStatus,
    val locale: String,
    val hidden: Boolean,
) {

    // MARK: - Init

    constructor(itemId: String, suggestedBy: String, photoUrl: String, suggestedAt: Instant, locale: String) :
        this(
            itemId = itemId,
            suggestedBy = suggestedBy,
            photoUrl = photoUrl,
            suggestedAt = suggestedAt.epochSecondsAsDouble(),
            status = TicketStatus.PENDING,
            locale = locale,
            hidden = false,
        )
}
