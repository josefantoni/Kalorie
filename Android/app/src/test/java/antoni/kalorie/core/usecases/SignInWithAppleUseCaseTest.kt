package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SignInWithAppleUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_linksThroughTheAppleWebFlow() = runTest {
        val fixture = makeSUT()

        fixture.sut()

        assertEquals(1, fixture.appleSignInProvider.linkCallCount)
    }

    @Test
    fun invoke_afterLinkingOrMerging_fillsTheEmptyProfileFieldsWithNameAndEmail() = runTest {
        val fixture = makeSUT(
            result = AppleAuthResult(displayName = "Josef Antoni", email = "josef@example.com", accessToken = null),
        )

        fixture.sut()

        assertEquals("an existing profile of the merged account must be filled, never replaced", 1, fixture.fillEmptyProfile.callCount)
        assertEquals("Josef Antoni", fixture.fillEmptyProfile.receivedDisplayName)
        assertEquals("josef@example.com", fixture.fillEmptyProfile.receivedEmail)
    }

    @Test
    fun invoke_whenAppleReturnsNoName_passesNullSoItCannotBlankTheStoredOne() = runTest {
        val fixture = makeSUT(
            result = AppleAuthResult(displayName = null, email = "josef@example.com", accessToken = null),
        )

        fixture.sut()

        assertNull(fixture.fillEmptyProfile.receivedDisplayName)
        assertEquals("josef@example.com", fixture.fillEmptyProfile.receivedEmail)
    }

    @Test
    fun invoke_whenLinkFails_propagatesErrorAndLeavesTheProfileAlone() = runTest {
        val fixture = makeSUT(linkError = RuntimeException("offline"))

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, fixture.fillEmptyProfile.callCount)
    }

    // MARK: - Helpers

    private class Fixture(
        val sut: SignInWithAppleUseCase,
        val appleSignInProvider: AppleSignInProviderFake,
        val fillEmptyProfile: FillEmptyProfileUseCaseFake,
    )

    private fun makeSUT(
        result: AppleAuthResult = AppleAuthResult(displayName = null, email = null, accessToken = null),
        linkError: Exception? = null,
    ): Fixture {
        val appleSignInProvider = AppleSignInProviderFake(result = result, linkError = linkError)
        val fillEmptyProfile = FillEmptyProfileUseCaseFake()
        val sut = SignInWithAppleUseCase(
            appleSignInProvider = appleSignInProvider,
            linkOrMergeCredential = LinkOrMergeCredentialUseCaseFake(),
            fillEmptyProfile = fillEmptyProfile,
        )
        return Fixture(sut, appleSignInProvider, fillEmptyProfile)
    }
}
