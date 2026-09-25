package antoni.kalorie.core.networking

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDTO(
    val displayName: String? = null,
    val email: String? = null,
)
