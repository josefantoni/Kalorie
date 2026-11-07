package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class FetchMaintainerClaimUseCaseTest {

    // MARK: - Tests

    @Test
    fun staleEntry_withoutForcedRefresh_readsTheCachedToken() = runTest {
        val (sut, authProvider) = makeSUT(forcesRefresh = false, entryAgeSeconds = staleAge)

        sut()

        assertEquals(listOf(false), authProvider.maintainerClaimRequests)
    }

    @Test
    fun staleEntry_withForcedRefresh_refreshesTheToken() = runTest {
        val (sut, authProvider) = makeSUT(forcesRefresh = true, entryAgeSeconds = staleAge)

        sut()

        assertEquals(listOf(true), authProvider.maintainerClaimRequests)
    }

    @Test
    fun emptyCache_neverForcesRefresh() = runTest {
        for (forcesRefresh in listOf(false, true)) {
            val (sut, authProvider) = makeSUT(forcesRefresh = forcesRefresh, entryAgeSeconds = null)

            sut()

            assertEquals(listOf(false), authProvider.maintainerClaimRequests)
        }
    }

    @Test
    fun freshEntry_doesNotCallTheProvider() = runTest {
        val (sut, authProvider) = makeSUT(forcesRefresh = true, entryAgeSeconds = 1)

        sut()

        assertTrue(authProvider.maintainerClaimRequests.isEmpty())
    }

    @Test
    fun missingUser_throwsNotAuthenticated() = runTest {
        val (sut, _) = makeSUT(forcesRefresh = false, entryAgeSeconds = null, userId = null)

        try {
            sut()
            fail("Expected AuthError.NotAuthenticated")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private val staleAge = Constants.Auth.MAINTAINER_CLAIM_CACHE_TTL_SECONDS + 1

    private fun makeSUT(
        forcesRefresh: Boolean,
        entryAgeSeconds: Long?,
        userId: String? = "user-1",
    ): Pair<FetchMaintainerClaimUseCase, AuthProviderFake> {
        val authProvider = AuthProviderFake(userId = userId)
        val cache = MaintainerClaimCache()
        if (entryAgeSeconds != null) {
            cache.entry = MaintainerClaimCache.Entry(value = true, cachedAt = Instant.now().minusSeconds(entryAgeSeconds), userId = "user-1")
        }
        return FetchMaintainerClaimUseCase(authProvider, cache, forcesRefresh) to authProvider
    }
}
