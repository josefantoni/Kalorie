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

        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = Instant.now())

        assertEquals(true, sut.entry?.value)
    }

    @Test
    fun entry_isSharedAcrossHoldersOfTheSameInstance() {
        val sut = MaintainerClaimCache()
        val otherHolder = sut

        sut.entry = MaintainerClaimCache.Entry(value = true, cachedAt = Instant.now())

        assertEquals(
            "the cache must be a reference type so every use case built from the same configurator sees the same value",
            true,
            otherHolder.entry?.value,
        )
    }
}
