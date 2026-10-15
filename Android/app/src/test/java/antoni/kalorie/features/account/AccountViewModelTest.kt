package antoni.kalorie.features.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInError
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.auth.MergeStatusReportingFake
import antoni.kalorie.core.usecases.DeleteAccountError
import antoni.kalorie.core.usecases.DeleteAccountUseCaseFake
import antoni.kalorie.core.usecases.DeleteAccountUseCaseProtocol
import antoni.kalorie.core.usecases.LinkAppleUseCaseFake
import antoni.kalorie.core.usecases.LinkAppleUseCaseProtocol
import antoni.kalorie.core.usecases.LinkCredentialError
import antoni.kalorie.core.usecases.LinkGoogleUseCaseFake
import antoni.kalorie.core.usecases.LinkGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.LinkOrMergeCredentialError
import antoni.kalorie.core.usecases.ReauthenticateError
import antoni.kalorie.core.usecases.ReauthenticateUseCaseFake
import antoni.kalorie.core.usecases.ReauthenticateUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithAppleUseCaseFake
import antoni.kalorie.core.usecases.SignInWithAppleUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseFake
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.SignOutUseCaseFake
import antoni.kalorie.core.usecases.SignOutUseCaseProtocol
import com.google.firebase.auth.FirebaseAuthWebException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountViewModelTest {

    @Test
    fun isAnonymous_reflectsAuthProvider() {
        assertTrue(makeSUT(authProvider = AuthProviderFake(isAnonymous = true)).isAnonymous.value)
    }

    @Test
    fun displayName_reflectsAuthProvider() {
        assertEquals("Josef", makeSUT(authProvider = AuthProviderFake(isAnonymous = false, displayName = "Josef")).displayName.value)
    }

    @Test
    fun displayName_fallsBackToProviderName_whenAuthNameIsMissing() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, providerDisplayName = "Josef Antoni"))
        assertEquals("Josef Antoni", sut.displayName.value)
    }

    @Test
    fun displayName_prefersAuthName_overProviderName() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, displayName = "Josef", providerDisplayName = "Josef Antoni"))
        assertEquals("Josef", sut.displayName.value)
    }

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
    fun onSignInWithGoogleTapped_whenNoGoogleAccountOnDevice_showsSpecificAlert() = runTest {
        val sut = makeSUT(signInWithGoogle = SignInWithGoogleUseCaseFake(errorToThrow = GoogleSignInError.NoGoogleAccount))

        sut.onSignInWithGoogleTapped()

        assertEquals("the user can fix a missing account by adding one, so the generic failure would hide the cure", R.string.account_error_noGoogleAccount, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
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
    fun onReauthenticateConfirmed_whenNoGoogleAccountOnDevice_showsSpecificAlert() = runTest {
        val sut = makeSUT(reauthenticate = ReauthenticateUseCaseFake(errorToThrow = GoogleSignInError.NoGoogleAccount))

        sut.onReauthenticateConfirmed()

        assertEquals(R.string.account_error_noGoogleAccount, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onReauthenticateConfirmed_whenGoogleCancelled_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(reauthenticate = ReauthenticateUseCaseFake(errorToThrow = GetCredentialCancellationException("cancelled")))

        sut.onReauthenticateConfirmed()

        assertNull("the user cancelled the dialog themselves, which is not an error worth reporting", sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onSignInWithAppleTapped_whenSucceeds_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT()

        sut.onSignInWithAppleTapped()

        assertNull(sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onSignInWithAppleTapped_whenWebFlowCancelled_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(signInWithApple = SignInWithAppleUseCaseFake(errorToThrow = FirebaseAuthWebException("ERROR_WEB_CONTEXT_CANCELED", "cancelled")))

        sut.onSignInWithAppleTapped()

        assertNull("the user closed the Apple page themselves, which is not an error worth reporting", sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onSignInWithAppleTapped_whenFailsWithOtherError_showsAlert() = runTest {
        val sut = makeSUT(signInWithApple = SignInWithAppleUseCaseFake(errorToThrow = RuntimeException("unknown")))

        sut.onSignInWithAppleTapped()

        assertEquals(R.string.account_error_signInFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onSignInWithAppleTapped_stillReportsMergeBeginAndEnd() = runTest {
        val mergeStatusReporting = MergeStatusReportingFake()
        val sut = makeSUT(
            signInWithApple = SignInWithAppleUseCaseFake(errorToThrow = FirebaseAuthWebException("ERROR_WEB_CONTEXT_CANCELED", "cancelled")),
            mergeStatusReporting = mergeStatusReporting,
        )

        sut.onSignInWithAppleTapped()

        assertEquals(1, mergeStatusReporting.beginMergeCallCount)
        assertEquals(1, mergeStatusReporting.endMergeCallCount)
    }

    @Test
    fun onReauthenticateConfirmed_whenWrongAccount_showsWrongAccountAlert() = runTest {
        val sut = makeSUT(reauthenticate = ReauthenticateUseCaseFake(errorToThrow = ReauthenticateError.WrongAccount))

        sut.onReauthenticateConfirmed()

        assertEquals("picking another identity needs its own message, not the generic sign-in failure", R.string.account_error_wrongAccount, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun linkedProviderKinds_reflectsAuthProvider() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, linkedProviderKinds = setOf(AuthProviderKind.GOOGLE)))
        assertEquals(setOf(AuthProviderKind.GOOGLE), sut.linkedProviderKinds.value)
    }

    @Test
    fun linkInfoText_whenOnlyGoogleLinked_offersLinkingApple() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, linkedProviderKinds = setOf(AuthProviderKind.GOOGLE)))
        assertEquals(R.string.account_linkInfo_google, sut.linkInfoText)
    }

    @Test
    fun linkInfoText_whenOnlyAppleLinked_offersLinkingGoogle() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, linkedProviderKinds = setOf(AuthProviderKind.APPLE)))
        assertEquals(R.string.account_linkInfo_apple, sut.linkInfoText)
    }

    @Test
    fun linkInfoText_whenBothLinked_saysSo() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, linkedProviderKinds = setOf(AuthProviderKind.APPLE, AuthProviderKind.GOOGLE)))
        assertEquals(R.string.account_linkInfo_both, sut.linkInfoText)
    }

    @Test
    fun linkInfoText_whenNoProviderLinked_isNil() {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, linkedProviderKinds = emptySet()))
        assertNull(sut.linkInfoText)
    }

    @Test
    fun onLinkProviderTapped_whenAppleSucceeds_tellsTheUserTheyCanSignInWithAppleAndReturnsToIdle() = runTest {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false))

        sut.onLinkProviderTapped(AuthProviderKind.APPLE)

        assertEquals(R.string.account_alert_linkSuccessApple, sut.alertItem.value?.titleRes)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onLinkProviderTapped_closesTheLinkInfoPopoverSoTheResultAlertCanBePresented() = runTest {
        val sut = makeSUT(
            authProvider = AuthProviderFake(isAnonymous = false),
            linkApple = LinkAppleUseCaseFake(errorToThrow = LinkCredentialError.AlreadyLinked),
        )
        sut.isLinkInfoPopoverVisible.value = true

        sut.onLinkProviderTapped(AuthProviderKind.APPLE)

        assertFalse(sut.isLinkInfoPopoverVisible.value)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onLinkProviderTapped_whenGoogleSucceeds_tellsTheUserTheyCanSignInWithGoogle() = runTest {
        val sut = makeSUT(authProvider = AuthProviderFake(isAnonymous = false))

        sut.onLinkProviderTapped(AuthProviderKind.GOOGLE)

        assertEquals(R.string.account_alert_linkSuccessGoogle, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_runsTheUseCaseOfTheChosenProvider() = runTest {
        val sut = makeSUT(linkApple = LinkAppleUseCaseFake(errorToThrow = RuntimeException("unknown")))

        sut.onLinkProviderTapped(AuthProviderKind.GOOGLE)

        assertEquals("linking Google must not run the Apple use case", R.string.account_alert_linkSuccessGoogle, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_whenAlreadyLinked_showsAlreadyLinkedAlert() = runTest {
        val sut = makeSUT(linkGoogle = LinkGoogleUseCaseFake(errorToThrow = LinkCredentialError.AlreadyLinked))
        sut.onLinkProviderTapped(AuthProviderKind.GOOGLE)
        assertEquals(R.string.account_error_linkAlreadyLinked, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_whenCredentialBelongsToAnotherAccount_explainsHowToProceed() = runTest {
        val sut = makeSUT(linkApple = LinkAppleUseCaseFake(errorToThrow = LinkCredentialError.CredentialBelongsToAnotherAccount))
        sut.onLinkProviderTapped(AuthProviderKind.APPLE)
        assertEquals(R.string.account_error_linkCredentialBelongsToAnotherAccount, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_whenEmailBelongsToAnotherAccount_showsEmailAlert() = runTest {
        val sut = makeSUT(linkApple = LinkAppleUseCaseFake(errorToThrow = LinkCredentialError.EmailBelongsToAnotherAccount))
        sut.onLinkProviderTapped(AuthProviderKind.APPLE)
        assertEquals(R.string.account_error_linkEmailBelongsToAnotherAccount, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_whenMergeInProgress_showsMergeInProgressAlert() = runTest {
        val sut = makeSUT(linkGoogle = LinkGoogleUseCaseFake(errorToThrow = LinkCredentialError.MergeInProgress))
        sut.onLinkProviderTapped(AuthProviderKind.GOOGLE)
        assertEquals(R.string.account_error_linkMergeInProgress, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onLinkProviderTapped_whenCancelled_showsNoAlertAndReturnsToIdle() = runTest {
        val sut = makeSUT(linkApple = LinkAppleUseCaseFake(errorToThrow = FirebaseAuthWebException("ERROR_WEB_CONTEXT_CANCELED", "cancelled")))

        sut.onLinkProviderTapped(AuthProviderKind.APPLE)

        assertNull("the user closed the prompt themselves, which is not an error worth reporting", sut.alertItem.value)
        assertEquals(AccountViewModel.State.IDLE, sut.state.value)
    }

    @Test
    fun onLinkProviderTapped_whenFailsWithOtherError_showsGenericAlert() = runTest {
        val sut = makeSUT(linkGoogle = LinkGoogleUseCaseFake(errorToThrow = RuntimeException("offline")))
        sut.onLinkProviderTapped(AuthProviderKind.GOOGLE)
        assertEquals(R.string.account_error_signInFailed, sut.alertItem.value?.titleRes)
    }

    // MARK: - Helpers

    private fun makeSUT(
        authProvider: AuthProviderProtocol = AuthProviderFake(),
        signOut: SignOutUseCaseProtocol = SignOutUseCaseFake(),
        signInWithApple: SignInWithAppleUseCaseProtocol = SignInWithAppleUseCaseFake(),
        signInWithGoogle: SignInWithGoogleUseCaseProtocol = SignInWithGoogleUseCaseFake(),
        linkApple: LinkAppleUseCaseProtocol = LinkAppleUseCaseFake(),
        linkGoogle: LinkGoogleUseCaseProtocol = LinkGoogleUseCaseFake(),
        deleteAccount: DeleteAccountUseCaseProtocol = DeleteAccountUseCaseFake(),
        reauthenticate: ReauthenticateUseCaseProtocol = ReauthenticateUseCaseFake(),
        mergeStatusReporting: MergeStatusReporting = MergeStatusReportingFake(),
    ): AccountViewModel = AccountViewModel(
        authProvider = authProvider,
        signOut = signOut,
        signInWithApple = signInWithApple,
        signInWithGoogle = signInWithGoogle,
        linkApple = linkApple,
        linkGoogle = linkGoogle,
        deleteAccount = deleteAccount,
        reauthenticate = reauthenticate,
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
