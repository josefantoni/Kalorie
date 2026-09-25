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

    data class Entry(val value: Boolean, val cachedAt: Instant, val userId: String)

    var entry: Entry? = null

    // MARK: - Functions

    fun freshValue(userId: String, now: Instant, ttlSeconds: Long): Boolean? {
        val cached = entry ?: return null
        if (cached.userId != userId) return null
        return if (Duration.between(cached.cachedAt, now).seconds < ttlSeconds) cached.value else null
    }
}

class FetchMaintainerClaimUseCase(
    private val cache: MaintainerClaimCache,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        cache.freshValue(user.uid, Instant.now(), Constants.Auth.MAINTAINER_CLAIM_CACHE_TTL_SECONDS)?.let { return it }
        val result = user.getIdToken(cache.entry != null).await()
        val isMaintainer = result.claims["maintainer"] as? Boolean ?: false
        cache.entry = MaintainerClaimCache.Entry(value = isMaintainer, cachedAt = Instant.now(), userId = user.uid)
        return isMaintainer
    }
}
