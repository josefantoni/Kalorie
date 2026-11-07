package antoni.kalorie.core.models

import antoni.kalorie.core.utils.Constants
import java.time.Instant

data class PhotoSuggestionDomain(
    val itemId: String,
    val suggestedBy: String,
    val photoUrl: String,
    val suggestedAt: Instant,
) {

    // MARK: - Properties

    val id: String
        get() = id(itemId, suggestedBy)

    // MARK: - Functions

    companion object {
        fun id(itemId: String, userId: String): String = "${itemId}_$userId"

        fun storagePath(itemId: String, userId: String): String = "${Constants.Storage.PHOTO_SUGGESTIONS_FOLDER}/$userId/$itemId.jpg"

        fun ids(documentId: String): Pair<String, String>? {
            val separator = documentId.indexOf('_')
            if (separator < 0) return null
            val itemId = documentId.substring(0, separator)
            val userId = documentId.substring(separator + 1)
            if (itemId.isEmpty() || userId.isEmpty()) return null
            return itemId to userId
        }
    }
}
