package antoni.kalorie.core.networking

import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class PhotoSuggestionDTO(
    @SerialName("item_id") val itemId: String,
    @SerialName("suggested_by") val suggestedBy: String,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("suggested_at") val suggestedAt: Double,
) {

    // MARK: - Init

    constructor(itemId: String, suggestedBy: String, photoUrl: String, suggestedAt: Instant) :
        this(itemId = itemId, suggestedBy = suggestedBy, photoUrl = photoUrl, suggestedAt = suggestedAt.epochSecondsAsDouble())

    // MARK: - Functions

    fun asDomain(): PhotoSuggestionDomain = PhotoSuggestionDomain(
        itemId = itemId,
        suggestedBy = suggestedBy,
        photoUrl = photoUrl,
        suggestedAt = instantFromEpochSeconds(suggestedAt),
    )
}
