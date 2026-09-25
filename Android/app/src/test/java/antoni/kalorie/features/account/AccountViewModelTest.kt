package antoni.kalorie.features.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.auth.MergeStatusReportingFake
import antoni.kalorie.core.usecases.DeleteAccountError
import antoni.kalorie.core.usecases.DeleteAccountUseCaseFake
import antoni.kalorie.core.usecases.DeleteAccountUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseFake
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.LinkOrMergeCredentialError
import antoni.kalorie.core.usecases.ReauthenticateUseCaseFake
import antoni.kalorie.core.usecases.ReauthenticateUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseFake
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.SignOutUseCaseFake
import antoni.kalorie.core.usecases.SignOutUseCaseProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountViewModelTest {

    // MARK: - onAppear

    @Test
    fun onAppear_whenMaintainerClaimIsTrue_setsIsMaintainer() = runTest {
        val sut = makeSUT(fetchMaintainerClaim = FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer = true))

        sut.onAppear()

        assertTrue(sut.isMaintainer.value)
    }

    @Test
    fun onAppear_whenMaintainerClaimIsFalse_leavesIsMaintainerFalse() = runTest {
        val sut = makeSUT(fetchMaintainerClaim = FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer = false))

        sut.onAppear()

        assertFalse(sut.isMaintainer.value)
    }

    // MARK: - Reading the auth provider

    @Test
    fun isAnonymous_reflectsAuthProvider() {
        assertTrue(makeSUT(authProvider = AuthProviderFake(isAnonymous = true)).isAnonymous.value)
    }

    @Test
    fun displayName_reflectsAuthProvider() {
        assertEquals("Josef", makeSUT(authProvider = AuthProviderFake(isAnonymous = false, displayName = "Josef")).displayName.value)
    }

    // MARK: - onSignOutTapped

    @Test
    fun onSignOutTapped_whenSucceeds_showsNoAlert() = runTest {
        val sut = makeSUT()

        sut.onSignOutTapped()

        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSignOutTapped_whenFails_showsAlert() = runTest {
        val sut = makeSUT(signOut = SignOutUseCaseFake(shouldThrow = true))

        sut.onSignOutTapped()

        assertEquals(R.string.account_error_signOutFailed, sut.alertItem.value?.titleRes)
    }

    // MARK: - onDeleteAccountConfirmed

    @Test
    fun onDeleteAccountConfirmed_whenSucceeds_showsNoAlert() = runTest {
        val sut = makeSUT()

        sut.onDeleteAccountConfirmed()

        assertNull(sut.alertItem.value)
    }

    @Test
    fun onDeleteAccountConfirmed_whenRequiresRecentLogin_showsReauthenticateAlert() = runTest {
        val sut = makeSUT(deleteAccount = DeleteAccountUseCaseFake(errorToThrow = DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = false)))

        sut.onDeleteAccountConfirmed()

        assertTrue("the user must get a way to actually sign in again, not just a dead-end alert", sut.isReauthenticateAlertVisible.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onDeleteAccountConfirmed_whenFailsWithOtherError_showsGenericAlert() = runTest {
        val sut = makeSUT(deleteAccount = DeleteAccountUseCaseFake(errorToThrow = RuntimeException("unknown")))

        sut.onDeleteAccountConfirmed()

        assertEquals(R.string.account_error_deleteFailed, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    // MARK: - onSignInWithGoogleTapped

    @Test
    fun onSignInWithGoogleTapped_whenSucceeds_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT()

        sut.onSignInWithGoogleTapped()

        assertNull(sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onSignInWithGoogleTapped_whenAccountExistsWithAnotherProvider_showsSpecificAlert() = runTest {
        val sut = makeSUT(signInWithGoogle = SignInWithGoogleUseCaseFake(errorToThrow = LinkOrMergeCredentialError.AccountExistsWithAnotherProvider))

        sut.onSignInWithGoogleTapped()

        assertEquals(R.string.account_error_accountExistsWithApple, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onSignInWithGoogleTapped_whenCancelled_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(signInWithGoogle = SignInWithGoogleUseCaseFake(errorToThrow = GetCredentialCancellationException("cancelled")))

        sut.onSignInWithGoogleTapped()

        assertNull("the user cancelled the dialog themselves, which is not an error worth reporting", sut.alertItem.value)
        assertEquals("a cancellation must not leave the view model in the linking state", AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onSignInWithGoogleTapped_whenFailsWithOtherError_showsAlert() = runTest {
        val sut = makeSUT(signInWithGoogle = SignInWithGoogleUseCaseFake(errorToThrow = RuntimeException("unknown")))

        sut.onSignInWithGoogleTapped()

        assertEquals(R.string.account_error_signInFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onSignInWithGoogleTapped_whenCancelled_stillReportsMergeBeginAndEnd() = runTest {
        val mergeStatusReporting = MergeStatusReportingFake()
        val sut = makeSUT(
            signInWithGoogle = SignInWithGoogleUseCaseFake(errorToThrow = GetCredentialCancellationException("cancelled")),
            mergeStatusReporting = mergeStatusReporting,
        )

        sut.onSignInWithGoogleTapped()

        assertEquals("beginMerge has already run by the time the cancellation is known", 1, mergeStatusReporting.beginMergeCallCount)
        assertEquals(1, mergeStatusReporting.endMergeCallCount)
    }

    // MARK: - onReauthenticateConfirmed

    @Test
    fun onReauthenticateConfirmed_whenSucceeds_retriesDeleteAndClearsAlert() = runTest {
        val deleteAccount = SequencedDeleteAccountUseCaseFake()
        deleteAccount.errorToThrow = DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = true)
        val sut = makeSUT(deleteAccount = deleteAccount, reauthenticate = ReauthenticateUseCaseFake())
        sut.onDeleteAccountConfirmed()
        assertTrue(sut.isReauthenticateAlertVisible.value)
        deleteAccount.errorToThrow = null

        sut.onReauthenticateConfirmed()

        assertEquals("a successful re-login must retry the delete, not just dismiss", 2, deleteAccount.callCount)
        assertEquals("the retry must skip re-wiping data the first attempt already deleted", listOf(false, true), deleteAccount.receivedSkipDataWipe)
        assertNull(sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onReauthenticateConfirmed_whenReauthFails_showsAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(reauthenticate = ReauthenticateUseCaseFake(errorToThrow = RuntimeException("unknown")))

        sut.onReauthenticateConfirmed()

        assertEquals(R.string.account_error_signInFailed, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onReauthenticateConfirmed_whenGoogleCancelled_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(reauthenticate = ReauthenticateUseCaseFake(errorToThrow = GetCredentialCancellationException("cancelled")))

        sut.onReauthenticateConfirmed()

        assertNull("the user cancelled the dialog themselves, which is not an error worth reporting", sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        authProvider: AuthProviderProtocol = AuthProviderFake(),
        signOut: SignOutUseCaseProtocol = SignOutUseCaseFake(),
        signInWithGoogle: SignInWithGoogleUseCaseProtocol = SignInWithGoogleUseCaseFake(),
        deleteAccount: DeleteAccountUseCaseProtocol = DeleteAccountUseCaseFake(),
        reauthenticate: ReauthenticateUseCaseProtocol = ReauthenticateUseCaseFake(),
        fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol = FetchMaintainerClaimUseCaseFake(),
        mergeStatusReporting: MergeStatusReporting = MergeStatusReportingFake(),
    ): AccountViewModel = AccountViewModel(
        authProvider = authProvider,
        signOut = signOut,
        signInWithGoogle = signInWithGoogle,
        deleteAccount = deleteAccount,
        reauthenticate = reauthenticate,
        fetchMaintainerClaim = fetchMaintainerClaim,
        mergeStatusReporting = mergeStatusReporting,
    )

    private class SequencedDeleteAccountUseCaseFake : DeleteAccountUseCaseProtocol {
        var callCount = 0
        val receivedSkipDataWipe = mutableListOf<Boolean>()
        var errorToThrow: Exception? = null

        override suspend fun invoke(skipDataWipe: Boolean) {
            callCount += 1
            receivedSkipDataWipe += skipDataWipe
            errorToThrow?.let { throw it }
        }
    }
}
