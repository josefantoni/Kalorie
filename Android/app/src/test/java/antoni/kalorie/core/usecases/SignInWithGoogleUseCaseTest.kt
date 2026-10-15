package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.GoogleSignInProviderFake
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInResult
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SignInWithGoogleUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_afterLinkingOrMerging_fillsTheEmptyProfileFieldsWithNameAndEmail() = runTest {
        val credential = GoogleAuthProvider.getCredential("id-token", null)
        val fixture = makeSUT(
            googleSignInProvider = GoogleSignInProviderFake(
                result = GoogleSignInResult(credential = credential, displayName = "Josef Antoni", email = "josef@example.com"),
            ),
        )

        fixture.sut()

        assertEquals("an existing profile of the merged account must be filled, never replaced", 1, fixture.fillEmptyProfile.callCount)
        assertEquals("Josef Antoni", fixture.fillEmptyProfile.receivedDisplayName)
        assertEquals("josef@example.com", fixture.fillEmptyProfile.receivedEmail)
    }

    @Test
    fun invoke_whenLinkOrMergeFails_propagatesErrorAndLeavesTheProfileAlone() = runTest {
        val fixture = makeSUT(linkOrMergeCredential = LinkOrMergeCredentialUseCaseFake(shouldThrow = true))

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, fixture.fillEmptyProfile.callCount)
    }

    @Test
    fun invoke_whenProviderFails_propagatesError() = runTest {
        val fixture = makeSUT(googleSignInProvider = GoogleSignInProviderFake(errorToThrow = RuntimeException("offline")))

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    @Test
    fun invoke_passesCredentialFromProviderToLinkOrMergeCredential() = runTest {
        val credential = GoogleAuthProvider.getCredential("id-token", null)
        val linkOrMergeCredential = LinkOrMergeCredentialCapturingFake()
        val fixture = makeSUT(
            googleSignInProvider = GoogleSignInProviderFake(result = GoogleSignInResult(credential = credential, displayName = null, email = null)),
            linkOrMergeCredential = linkOrMergeCredential,
        )

        fixture.sut()

        assertEquals(1, linkOrMergeCredential.callCount)
        assertEquals("google.com", linkOrMergeCredential.receivedCredential?.provider)
    }

    // MARK: - Helpers

    private class Fixture(
        val sut: SignInWithGoogleUseCase,
        val fillEmptyProfile: FillEmptyProfileUseCaseFake,
    )

    private fun makeSUT(
        googleSignInProvider: GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
        linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol = LinkOrMergeCredentialUseCaseFake(),
    ): Fixture {
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = SignInWithGoogleUseCase(
            googleSignInProvider = googleSignInProvider,
            linkOrMergeCredential = linkOrMergeCredential,
            fillEmptyProfile = fillEmptyProfile,
        )
        return Fixture(sut, fillEmptyProfile)
    }

    private class LinkOrMergeCredentialCapturingFake : LinkOrMergeCredentialUseCaseProtocol {
        var callCount = 0
        var receivedCredential: AuthCredential? = null

        override suspend fun invoke(credential: AuthCredential) {
            callCount += 1
            receivedCredential = credential
        }

        override suspend fun linkOrMerge(link: suspend () -> Unit) = link()
    }
}
