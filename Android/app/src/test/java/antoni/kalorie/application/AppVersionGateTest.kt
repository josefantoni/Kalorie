package antoni.kalorie.application

import antoni.kalorie.core.models.AppVersion
import antoni.kalorie.core.usecases.FetchMinimumVersionUseCaseFake
import antoni.kalorie.core.usecases.FetchMinimumVersionUseCaseProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionGateTest {

    // MARK: - Tests

    @Test
    fun check_whenCurrentIsBelowMinimum_requiresUpdate() = runTest {
        val sut = makeSUT(current = "1.0", minimum = "1.0.1")

        sut.check()

        assertTrue(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenCurrentEqualsMinimum_doesNotRequireUpdate() = runTest {
        val sut = makeSUT(current = "1.0.1", minimum = "1.0.1")

        sut.check()

        assertFalse(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenNoMinimumIsSet_doesNotRequireUpdate() = runTest {
        val sut = makeSUT(current = "1.0", minimum = null)

        sut.check()

        assertFalse(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenCurrentVersionDoesNotParse_doesNotRequireUpdate() = runTest {
        val sut = makeSUT(current = null, minimum = "9.0")

        sut.check()

        assertFalse(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenReadFails_doesNotRequireUpdate() = runTest {
        val sut = makeSUT(current = "1.0", minimum = "9.0", shouldThrow = true)

        sut.check()

        assertFalse(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenReadFailsAfterBlocking_keepsBlocking() = runTest {
        val fetch = ToggleableFetchMinimumVersionUseCase(minimum = AppVersion.parse("2.0"))
        val sut = AppVersionGate(fetch, AppVersion.parse("1.0"))
        sut.check()

        fetch.shouldThrow = true
        sut.check()

        assertTrue(sut.isUpdateRequired.value)
    }

    @Test
    fun check_whenMinimumIsLoweredAfterBlocking_unblocks() = runTest {
        val fetch = ToggleableFetchMinimumVersionUseCase(minimum = AppVersion.parse("2.0"))
        val sut = AppVersionGate(fetch, AppVersion.parse("1.0"))
        sut.check()

        fetch.minimum = null
        sut.check()

        assertFalse(sut.isUpdateRequired.value)
    }

    // MARK: - Helpers

    private fun makeSUT(current: String?, minimum: String?, shouldThrow: Boolean = false): AppVersionGate {
        val fetch = FetchMinimumVersionUseCaseFake(stubbedMinimum = minimum?.let(AppVersion::parse), shouldThrow = shouldThrow)
        return AppVersionGate(fetch, current?.let(AppVersion::parse))
    }
}

private class ToggleableFetchMinimumVersionUseCase(
    var minimum: AppVersion?,
    var shouldThrow: Boolean = false,
) : FetchMinimumVersionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): AppVersion? {
        if (shouldThrow) throw RuntimeException("unknown")
        return minimum
    }
}
