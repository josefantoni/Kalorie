package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.tasks.await

interface FetchMaintainerClaimUseCaseProtocol {
    suspend operator fun invoke(): Boolean
}

class MaintainerClaimCache {

    // MARK: - Properties

    data class Entry(val value: Boolean, val cachedAt: Instant)

    var entry: Entry? = null
}

class FetchMaintainerClaimUseCase(
    private val cache: MaintainerClaimCache,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean {
        val cached = cache.entry
        if (cached != null && Duration.between(cached.cachedAt, Instant.now()).seconds < Constants.Auth.MAINTAINER_CLAIM_CACHE_TTL_SECONDS) {
            return cached.value
        }
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        val result = user.getIdToken(cache.entry != null).await()
        val isMaintainer = result.claims["maintainer"] as? Boolean ?: false
        cache.entry = MaintainerClaimCache.Entry(value = isMaintainer, cachedAt = Instant.now())
        return isMaintainer
    }
}
