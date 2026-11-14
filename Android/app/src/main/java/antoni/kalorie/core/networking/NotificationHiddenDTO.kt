package antoni.kalorie.core.networking

import kotlinx.serialization.Serializable

@Serializable
data class NotificationHiddenDTO(
    val hidden: Boolean,
) {

    // MARK: - Init

    constructor() : this(hidden = true)
}
