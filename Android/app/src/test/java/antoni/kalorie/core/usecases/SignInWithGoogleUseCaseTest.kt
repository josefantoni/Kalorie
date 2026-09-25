package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.GoogleSignInProviderFake
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInResult
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.UserProfileDTO
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SignInWithGoogleUseCaseTest {

    // MARK: - Tests

    @Test
    fun saveProfileIfNeeded_withNameAndEmail_updatesDisplayNameAndSavesProfile() = runTest {
        val fixture = makeSUT()

        fixture.sut.saveProfileIfNeeded(displayName = "Josef Antoni", email = "josef@example.com")

        val profile = fixture.dataProvider.setSavedItem as UserProfileDTO
        assertEquals(1, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertEquals("Josef Antoni", profile.displayName)
        assertEquals("josef@example.com", profile.email)
        assertEquals("users", fixture.dataProvider.setSavedCollection)
        assertEquals("user-1", fixture.dataProvider.setSavedId)
    }

    @Test
    fun saveProfileIfNeeded_withNullNameAndEmail_doesNothing() = runTest {
        val fixture = makeSUT()

        fixture.sut.saveProfileIfNeeded(displayName = null, email = null)

        assertEquals(0, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertNull(fixture.dataProvider.setSavedItem)
    }

    @Test
    fun saveProfileIfNeeded_withEmailOnly_doesNotUpdateDisplayNameButSavesProfile() = runTest {
        val fixture = makeSUT()

        fixture.sut.saveProfileIfNeeded(displayName = null, email = "josef@example.com")

        val profile = fixture.dataProvider.setSavedItem as UserProfileDTO
        assertEquals("without a name there is nothing to write to the Auth profile", 0, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertNull(profile.displayName)
        assertEquals("josef@example.com", profile.email)
    }

    @Test
    fun saveProfileIfNeeded_whenNotAuthenticated_updatesDisplayNameButDoesNotSaveProfile() = runTest {
        val fixture = makeSUT(userId = null)

        fixture.sut.saveProfileIfNeeded(displayName = "Josef Antoni", email = "josef@example.com")

        assertEquals(1, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertNull("without a userId there is nowhere to save the profile", fixture.dataProvider.setSavedItem)
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
        val authCommandProvider: AuthCommandProviderFake,
        val dataProvider: FirestoreDataProviderFake,
    )

    private fun makeSUT(
        googleSignInProvider: GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
        linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol = LinkOrMergeCredentialUseCaseFake(),
        userId: String? = "user-1",
    ): Fixture {
        val authCommandProvider = AuthCommandProviderFake()
        val dataProvider = FirestoreDataProviderFake()
        val sut = SignInWithGoogleUseCase(
            googleSignInProvider = googleSignInProvider,
            linkOrMergeCredential = linkOrMergeCredential,
            authCommandProvider = authCommandProvider,
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = userId),
        )
        return Fixture(sut, authCommandProvider, dataProvider)
    }

    private class LinkOrMergeCredentialCapturingFake : LinkOrMergeCredentialUseCaseProtocol {
        var callCount = 0
        var receivedCredential: AuthCredential? = null

        override suspend fun invoke(credential: AuthCredential) {
            callCount += 1
            receivedCredential = credential
        }
    }
}
