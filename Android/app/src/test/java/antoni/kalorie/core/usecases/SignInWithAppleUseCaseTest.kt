package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderFake
import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SignInWithAppleUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_linksThroughTheAppleWebFlow() = runTest {
        val (sut, appleSignInProvider, _, _) = makeSUT()

        sut()

        assertEquals(1, appleSignInProvider.linkCallCount)
    }

    @Test
    fun invoke_whenAppleReturnsNameAndEmail_updatesDisplayNameAndSavesProfile() = runTest {
        val (sut, _, authCommandProvider, dataProvider) = makeSUT(
            result = AppleAuthResult(displayName = "Josef Antoni", email = "josef@example.com", accessToken = null),
        )

        sut()

        assertEquals(1, authCommandProvider.updateDisplayNameCallCount)
        assertNotNull(dataProvider.setSavedItem)
    }

    @Test
    fun invoke_whenAppleReturnsNothing_doesNothingToTheProfile() = runTest {
        val (sut, _, authCommandProvider, dataProvider) = makeSUT()

        sut()

        assertEquals(0, authCommandProvider.updateDisplayNameCallCount)
        assertNull("the web flow may not pass the name to Firebase, and an empty profile must not be written", dataProvider.setSavedItem)
    }

    @Test
    fun invoke_whenLinkFails_propagatesErrorAndSavesNoProfile() = runTest {
        val (sut, _, authCommandProvider, _) = makeSUT(linkError = RuntimeException("offline"))

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, authCommandProvider.updateDisplayNameCallCount)
    }

    // MARK: - Helpers

    private data class Fixture(
        val sut: SignInWithAppleUseCase,
        val appleSignInProvider: AppleSignInProviderFake,
        val authCommandProvider: AuthCommandProviderFake,
        val dataProvider: FirestoreDataProviderFake,
    )

    private fun makeSUT(
        result: AppleAuthResult = AppleAuthResult(displayName = null, email = null, accessToken = null),
        linkError: Exception? = null,
    ): Fixture {
        val appleSignInProvider = AppleSignInProviderFake(result = result, linkError = linkError)
        val authCommandProvider = AuthCommandProviderFake()
        val dataProvider = FirestoreDataProviderFake()
        val sut = SignInWithAppleUseCase(
            appleSignInProvider = appleSignInProvider,
            linkOrMergeCredential = LinkOrMergeCredentialUseCaseFake(),
            authCommandProvider = authCommandProvider,
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = "user-1"),
        )
        return Fixture(sut, appleSignInProvider, authCommandProvider, dataProvider)
    }
}
