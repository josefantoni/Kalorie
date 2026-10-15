package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinkAppleUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_linksThroughTheAppleWebFlowThenFillsTheProfile() = runTest {
        val appleSignInProvider = AppleSignInProviderFake(result = AppleAuthResult(displayName = "Josef Antoni", email = "josef@example.com", accessToken = null))
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkAppleUseCase(appleSignInProvider, LinkCredentialUseCaseFake(), fillEmptyProfile)

        sut()

        assertEquals(1, appleSignInProvider.linkCallCount)
        assertEquals("Josef Antoni", fillEmptyProfile.receivedDisplayName)
        assertEquals("josef@example.com", fillEmptyProfile.receivedEmail)
    }

    @Test
    fun invoke_whenLinkIsRefused_propagatesErrorAndLeavesTheProfileAlone() = runTest {
        val appleSignInProvider = AppleSignInProviderFake()
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkAppleUseCase(appleSignInProvider, LinkCredentialUseCaseFake(errorToThrow = LinkCredentialError.CredentialBelongsToAnotherAccount), fillEmptyProfile)

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: LinkCredentialError.CredentialBelongsToAnotherAccount) {
        }

        assertEquals("a refused link must not touch the signed-in account's profile", 0, fillEmptyProfile.callCount)
        assertEquals(0, appleSignInProvider.linkCallCount)
    }

    @Test
    fun invoke_whenTheWebFlowFails_propagatesErrorAndLeavesTheProfileAlone() = runTest {
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkAppleUseCase(AppleSignInProviderFake(linkError = RuntimeException("cancelled")), LinkCredentialUseCaseFake(), fillEmptyProfile)

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, fillEmptyProfile.callCount)
    }
}
