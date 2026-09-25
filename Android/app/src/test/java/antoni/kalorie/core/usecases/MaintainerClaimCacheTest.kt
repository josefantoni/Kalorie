package antoni.kalorie.core.usecases

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintainerClaimCacheTest {

    // MARK: - Tests

    @Test
    fun entry_whenNothingStoredYet_isNull() {
        assertNull(MaintainerClaimCache().entry)
    }

    @Test
    fun entry_afterStoring_returnsStoredValue() {
        val sut = MaintainerClaimCache()

        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = Instant.now(), userId = "user-1")

        assertEquals(true, sut.entry?.value)
    }

    @Test
    fun entry_isSharedAcrossHoldersOfTheSameInstance() {
        val sut = MaintainerClaimCache()
        val otherHolder = sut

        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = Instant.now(), userId = "user-1")

        assertEquals(
            "the cache must be a reference type so every use case built from the same configurator sees the same value",
            true,
            otherHolder.entry?.value,
        )
    }

    @Test
    fun freshValue_forTheSameUserWithinTheTimeToLive_returnsTheStoredValue() {
        val now = Instant.now()
        val sut = MaintainerClaimCache()
        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = now.minusSeconds(10), userId = "user-1")

        assertEquals(true, sut.freshValue(userId = "user-1", now = now, ttlSeconds = 300))
    }

    @Test
    fun freshValue_afterTheTimeToLive_returnsNull() {
        val now = Instant.now()
        val sut = MaintainerClaimCache()
        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = now.minusSeconds(301), userId = "user-1")

        assertNull(sut.freshValue(userId = "user-1", now = now, ttlSeconds = 300))
    }

    @Test
    fun freshValue_forADifferentUser_returnsNullSoTheClaimIsNeverServedToAnotherAccount() {
        val now = Instant.now()
        val sut = MaintainerClaimCache()
        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = now.minusSeconds(10), userId = "maintainer")

        assertNull(sut.freshValue(userId = "someone-else", now = now, ttlSeconds = 300))
    }
}
