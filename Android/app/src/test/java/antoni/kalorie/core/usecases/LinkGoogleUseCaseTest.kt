package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.GoogleSignInError
import antoni.kalorie.core.auth.GoogleSignInProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinkGoogleUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_linksTheGoogleCredentialThenFillsTheProfile() = runTest {
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkGoogleUseCase(GoogleSignInProviderFake(), LinkCredentialUseCaseFake(), fillEmptyProfile)

        sut()

        assertEquals("Josef Antoni", fillEmptyProfile.receivedDisplayName)
        assertEquals("josef@example.com", fillEmptyProfile.receivedEmail)
    }

    @Test
    fun invoke_whenLinkIsRefused_propagatesErrorAndLeavesTheProfileAlone() = runTest {
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkGoogleUseCase(GoogleSignInProviderFake(), LinkCredentialUseCaseFake(errorToThrow = LinkCredentialError.EmailBelongsToAnotherAccount), fillEmptyProfile)

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: LinkCredentialError.EmailBelongsToAnotherAccount) {
        }

        assertEquals("a refused link must not touch the signed-in account's profile", 0, fillEmptyProfile.callCount)
    }

    @Test
    fun invoke_whenGoogleSignInFails_propagatesErrorAndDoesNotFillTheProfile() = runTest {
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = LinkGoogleUseCase(GoogleSignInProviderFake(errorToThrow = GoogleSignInError.UnexpectedCredential), LinkCredentialUseCaseFake(), fillEmptyProfile)

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: GoogleSignInError.UnexpectedCredential) {
        }

        assertEquals(0, fillEmptyProfile.callCount)
    }
}
