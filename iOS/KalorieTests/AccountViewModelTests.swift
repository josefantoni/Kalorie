//
//  AccountViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import GoogleSignIn
import XCTest
@testable import Kalorie

final class AccountViewModelTests: XCTestCase {

    // MARK: - Tests

    func test_isAnonymous_reflectsAuthProvider() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: true))
        XCTAssertTrue(sut.isAnonymous)
    }

    func test_displayName_reflectsAuthProvider() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, displayName: "Josef"))
        XCTAssertEqual(sut.displayName, "Josef")
    }

    func test_displayName_fallsBackToProviderName_whenAuthNameIsMissing() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, providerDisplayName: "Josef Antoni"))
        XCTAssertEqual(sut.displayName, "Josef Antoni")
    }

    func test_displayName_prefersAuthName_overProviderName() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, displayName: "Josef", providerDisplayName: "Josef Antoni"))
        XCTAssertEqual(sut.displayName, "Josef")
    }

    func test_onSignOutTapped_whenSucceeds_showsNoAlert() {
        let sut = makeSUT()
        sut.onSignOutTapped()
        XCTAssertNil(sut.alertItem)
    }

    func test_onSignOutTapped_whenFails_showsAlert() {
        let sut = makeSUT(signOut: SignOutUseCaseFake(shouldThrow: true))
        sut.onSignOutTapped()
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteAccountConfirmed_whenSucceeds_showsNoAlert() async {
        let sut = makeSUT()
        await sut.onDeleteAccountConfirmed()
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteAccountConfirmed_whenRequiresRecentLogin_showsReauthenticateAlert() async {
        let sut = makeSUT(deleteAccount: DeleteAccountUseCaseFake(errorToThrow: DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: false)))
        await sut.onDeleteAccountConfirmed()
        XCTAssertTrue(sut.isReauthenticateAlertVisible, "the user must get a way to actually sign in again, not just a dead-end alert")
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteAccountConfirmed_whenFailsWithOtherError_showsGenericAlert() async {
        let sut = makeSUT(deleteAccount: DeleteAccountUseCaseFake(errorToThrow: URLError(.unknown)))
        await sut.onDeleteAccountConfirmed()
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorDeleteFailed)
    }

    @MainActor
    func test_onDeleteAccountConfirmed_whenAppleIdentityIsWrong_showsWrongAccountAlert() async {
        let sut = makeSUT(deleteAccount: DeleteAccountUseCaseFake(errorToThrow: ReauthenticateError.wrongAccount))
        await sut.onDeleteAccountConfirmed()
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorWrongAccount)
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenSucceeds_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT()

        await sut.onSignInWithAppleTapped()

        XCTAssertNil(sut.alertItem)
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenSucceeds_showsTheProviderTheUserSignedInWithAsLinked() async {
        let authProvider = AuthProviderMutableFake()
        let sut = makeSUT(
            authProvider: authProvider,
            signInWithGoogle: SignInUseCaseStub { authProvider.linkedProviderKinds = [.google] }
        )
        XCTAssertEqual(sut.linkedProviderKinds, [])

        await sut.onSignInWithGoogleTapped()

        XCTAssertEqual(sut.linkedProviderKinds, [.google], "a freshly signed-in user must see their provider ticked, not both tiles offering to link it")
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenSucceeds_showsTheProviderTheUserSignedInWithAsLinked() async {
        let authProvider = AuthProviderMutableFake()
        let sut = makeSUT(
            authProvider: authProvider,
            signInWithApple: SignInUseCaseStub { authProvider.linkedProviderKinds = [.apple] }
        )

        await sut.onSignInWithAppleTapped()

        XCTAssertEqual(sut.linkedProviderKinds, [.apple])
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenCancelled_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT(signInWithApple: SignInWithAppleUseCaseFake(errorToThrow: makeAuthorizationError(.canceled)))

        await sut.onSignInWithAppleTapped()

        XCTAssertNil(sut.alertItem, "uživatel zrušil dialog sám — nejde o chybu, kterou je třeba hlásit")
        XCTAssertEqual(sut.state, .idle, "zrušení nesmí nechat viewModel v .linking stavu")
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenFailsWithOtherError_showsAlert() async {
        let sut = makeSUT(signInWithApple: SignInWithAppleUseCaseFake(errorToThrow: makeAuthorizationError(.failed)))

        await sut.onSignInWithAppleTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorSignInFailed)
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenCancelled_stillReportsMergeBeginAndEnd() async {
        let mergeStatusReporting = MergeStatusReportingFake()
        let sut = makeSUT(
            signInWithApple: SignInWithAppleUseCaseFake(errorToThrow: makeAuthorizationError(.canceled)),
            mergeStatusReporting: mergeStatusReporting
        )

        await sut.onSignInWithAppleTapped()

        XCTAssertEqual(mergeStatusReporting.beginMergeCallCount, 1, "narozdíl od dřívějšího chování už beginMerge proběhlo, než přišla informace o zrušení")
        XCTAssertEqual(mergeStatusReporting.endMergeCallCount, 1)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenSucceeds_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT()

        await sut.onSignInWithGoogleTapped()

        XCTAssertNil(sut.alertItem)
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onSignInWithAppleTapped_whenAccountExistsWithAnotherProvider_showsSpecificAlert() async {
        let sut = makeSUT(
            signInWithApple: SignInWithAppleUseCaseFake(errorToThrow: LinkOrMergeCredentialError.accountExistsWithAnotherProvider)
        )

        await sut.onSignInWithAppleTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorAccountExistsWithGoogle)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenAccountExistsWithAnotherProvider_showsSpecificAlert() async {
        let sut = makeSUT(
            signInWithGoogle: SignInWithGoogleUseCaseFake(errorToThrow: LinkOrMergeCredentialError.accountExistsWithAnotherProvider)
        )

        await sut.onSignInWithGoogleTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorAccountExistsWithApple)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenCancelled_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT(
            signInWithGoogle: SignInWithGoogleUseCaseFake(errorToThrow: makeGoogleSignInError(.canceled))
        )

        await sut.onSignInWithGoogleTapped()

        XCTAssertNil(sut.alertItem, "uživatel zrušil dialog sám — nejde o chybu, kterou je třeba hlásit")
        XCTAssertEqual(sut.state, .idle, "zrušení nesmí nechat viewModel v .linking stavu")
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenFailsWithOtherError_showsAlert() async {
        let sut = makeSUT(signInWithGoogle: SignInWithGoogleUseCaseFake(errorToThrow: URLError(.unknown)))

        await sut.onSignInWithGoogleTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorSignInFailed)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenCancelled_stillReportsMergeBeginAndEnd() async {
        let mergeStatusReporting = MergeStatusReportingFake()
        let sut = makeSUT(
            signInWithGoogle: SignInWithGoogleUseCaseFake(errorToThrow: makeGoogleSignInError(.canceled)),
            mergeStatusReporting: mergeStatusReporting
        )

        await sut.onSignInWithGoogleTapped()

        XCTAssertEqual(mergeStatusReporting.beginMergeCallCount, 1, "narozdíl od Apple flow tady beginMerge už proběhlo, než přišla informace o zrušení")
        XCTAssertEqual(mergeStatusReporting.endMergeCallCount, 1)
    }

    @MainActor
    func test_onReauthenticateConfirmed_whenSucceeds_retriesDeleteAndClearsAlert() async {
        let deleteAccount = SequencedDeleteAccountUseCaseFake()
        deleteAccount.errorToThrow = DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: true)
        let sut = makeSUT(deleteAccount: deleteAccount, reauthenticate: ReauthenticateUseCaseFake())
        await sut.onDeleteAccountConfirmed()
        XCTAssertTrue(sut.isReauthenticateAlertVisible)

        deleteAccount.errorToThrow = nil
        await sut.onReauthenticateConfirmed()

        XCTAssertEqual(deleteAccount.callCount, 2, "a successful re-login must retry the delete, not just dismiss")
        XCTAssertEqual(deleteAccount.receivedSkipDataWipe, [false, true], "the retry must skip re-wiping data the first attempt already deleted")
        XCTAssertNil(sut.alertItem)
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onReauthenticateConfirmed_whenReauthFails_showsAlertAndReturnsToIdle() async {
        let sut = makeSUT(reauthenticate: ReauthenticateUseCaseFake(errorToThrow: URLError(.unknown)))

        await sut.onReauthenticateConfirmed()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorSignInFailed)
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onReauthenticateConfirmed_whenAppleCancelled_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT(reauthenticate: ReauthenticateUseCaseFake(errorToThrow: makeAuthorizationError(.canceled)))

        await sut.onReauthenticateConfirmed()

        XCTAssertNil(sut.alertItem, "the user cancelled the dialog themselves — not an error worth reporting")
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onReauthenticateConfirmed_whenGoogleCancelled_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT(reauthenticate: ReauthenticateUseCaseFake(errorToThrow: makeGoogleSignInError(.canceled)))

        await sut.onReauthenticateConfirmed()

        XCTAssertNil(sut.alertItem, "the user cancelled the dialog themselves — not an error worth reporting")
        XCTAssertEqual(sut.state, .idle)
    }

    func test_linkedProviderKinds_reflectsAuthProvider() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, linkedProviderKinds: [.google]))
        XCTAssertEqual(sut.linkedProviderKinds, [.google])
    }

    func test_linkInfoText_whenOnlyGoogleLinked_offersLinkingApple() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, linkedProviderKinds: [.google]))
        XCTAssertEqual(sut.linkInfoText, L10n.Account.linkInfoGoogle)
    }

    func test_linkInfoText_whenOnlyAppleLinked_offersLinkingGoogle() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, linkedProviderKinds: [.apple]))
        XCTAssertEqual(sut.linkInfoText, L10n.Account.linkInfoApple)
    }

    func test_linkInfoText_whenBothLinked_saysSo() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, linkedProviderKinds: [.apple, .google]))
        XCTAssertEqual(sut.linkInfoText, L10n.Account.linkInfoBoth)
    }

    func test_linkInfoText_whenNoProviderLinked_isNil() {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false, linkedProviderKinds: []))
        XCTAssertNil(sut.linkInfoText)
    }

    @MainActor
    func test_onLinkProviderTapped_whenAppleSucceeds_tellsTheUserTheyCanSignInWithAppleAndReturnsToIdle() async {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false))

        await sut.onLinkProviderTapped(.apple)

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.alertLinkSuccessApple)
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onLinkProviderTapped_closesTheLinkInfoPopoverSoTheResultAlertCanBePresented() async {
        let sut = makeSUT(
            authProvider: AuthProviderFake(isAnonymous: false),
            linkApple: LinkAppleUseCaseFake(errorToThrow: LinkCredentialError.alreadyLinked)
        )
        sut.isLinkInfoPopoverVisible = true

        await sut.onLinkProviderTapped(.apple)

        XCTAssertFalse(sut.isLinkInfoPopoverVisible, "an alert cannot be presented while the popover is still up")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onLinkProviderTapped_whenGoogleSucceeds_tellsTheUserTheyCanSignInWithGoogle() async {
        let sut = makeSUT(authProvider: AuthProviderFake(isAnonymous: false))

        await sut.onLinkProviderTapped(.google)

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.alertLinkSuccessGoogle)
    }

    @MainActor
    func test_onLinkProviderTapped_runsTheUseCaseOfTheChosenProvider() async {
        let sut = makeSUT(
            authProvider: AuthProviderFake(isAnonymous: false),
            linkApple: LinkAppleUseCaseFake(errorToThrow: URLError(.unknown))
        )

        await sut.onLinkProviderTapped(.google)

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.alertLinkSuccessGoogle, "linking Google must not run the Apple use case")
    }

    @MainActor
    func test_onLinkProviderTapped_whenAlreadyLinked_showsAlreadyLinkedAlert() async {
        let sut = makeSUT(linkGoogle: LinkGoogleUseCaseFake(errorToThrow: LinkCredentialError.alreadyLinked))
        await sut.onLinkProviderTapped(.google)
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorLinkAlreadyLinked)
    }

    @MainActor
    func test_onLinkProviderTapped_whenCredentialBelongsToAnotherAccount_explainsHowToProceed() async {
        let sut = makeSUT(linkApple: LinkAppleUseCaseFake(errorToThrow: LinkCredentialError.credentialBelongsToAnotherAccount))
        await sut.onLinkProviderTapped(.apple)
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorLinkCredentialOfAnotherAccount)
    }

    @MainActor
    func test_onLinkProviderTapped_whenEmailBelongsToAnotherAccount_showsEmailAlert() async {
        let sut = makeSUT(linkApple: LinkAppleUseCaseFake(errorToThrow: LinkCredentialError.emailBelongsToAnotherAccount))
        await sut.onLinkProviderTapped(.apple)
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorLinkEmailBelongsToAnotherAccount)
    }

    @MainActor
    func test_onLinkProviderTapped_whenMergeInProgress_showsMergeInProgressAlert() async {
        let sut = makeSUT(linkGoogle: LinkGoogleUseCaseFake(errorToThrow: LinkCredentialError.mergeInProgress))
        await sut.onLinkProviderTapped(.google)
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorLinkMergeInProgress)
    }

    @MainActor
    func test_onLinkProviderTapped_whenCancelled_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT(linkApple: LinkAppleUseCaseFake(errorToThrow: makeAuthorizationError(.canceled)))

        await sut.onLinkProviderTapped(.apple)

        XCTAssertNil(sut.alertItem, "the user dismissed the prompt themselves — not an error worth reporting")
        XCTAssertEqual(sut.state, .idle)
    }

    @MainActor
    func test_onLinkProviderTapped_whenFailsWithOtherError_showsGenericAlert() async {
        let sut = makeSUT(linkGoogle: LinkGoogleUseCaseFake(errorToThrow: URLError(.notConnectedToInternet)))
        await sut.onLinkProviderTapped(.google)
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorSignInFailed)
    }

    @MainActor
    func test_onReauthenticateConfirmed_whenWrongAccount_showsWrongAccountAlert() async {
        let sut = makeSUT(reauthenticate: ReauthenticateUseCaseFake(errorToThrow: ReauthenticateError.wrongAccount))

        await sut.onReauthenticateConfirmed()

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorWrongAccount, "picking another identity needs its own message, not the generic sign-in failure")
        XCTAssertEqual(sut.state, .idle)
    }

    // MARK: - Helpers

    private func makeSUT(
        authProvider: any AuthProviderProtocol = AuthProviderFake(),
        signOut: any SignOutUseCaseProtocol = SignOutUseCaseFake(),
        signInWithApple: any SignInWithAppleUseCaseProtocol = SignInWithAppleUseCaseFake(),
        signInWithGoogle: any SignInWithGoogleUseCaseProtocol = SignInWithGoogleUseCaseFake(),
        linkApple: any LinkAppleUseCaseProtocol = LinkAppleUseCaseFake(),
        linkGoogle: any LinkGoogleUseCaseProtocol = LinkGoogleUseCaseFake(),
        deleteAccount: any DeleteAccountUseCaseProtocol = DeleteAccountUseCaseFake(),
        reauthenticate: any ReauthenticateUseCaseProtocol = ReauthenticateUseCaseFake(),
        mergeStatusReporting: any MergeStatusReporting = MergeStatusReportingFake()
    ) -> AccountViewModel {
        let sut = AccountViewModel(
            authProvider: authProvider,
            signOut: signOut,
            signInWithApple: signInWithApple,
            signInWithGoogle: signInWithGoogle,
            linkApple: linkApple,
            linkGoogle: linkGoogle,
            deleteAccount: deleteAccount,
            reauthenticate: reauthenticate,
            mergeStatusReporting: mergeStatusReporting
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "AccountViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeAuthorizationError(_ code: ASAuthorizationError.Code) -> NSError {
        NSError(domain: ASAuthorizationError.errorDomain, code: code.rawValue)
    }

    private func makeGoogleSignInError(_ code: GIDSignInError.Code) -> NSError {
        NSError(domain: GIDSignInError.errorDomain, code: code.rawValue)
    }
}

private final class SequencedDeleteAccountUseCaseFake: DeleteAccountUseCaseProtocol {
    private(set) var callCount = 0
    private(set) var receivedSkipDataWipe: [Bool] = []
    var errorToThrow: Error?

    func callAsFunction(skipDataWipe: Bool) async throws {
        callCount += 1
        receivedSkipDataWipe.append(skipDataWipe)
        if let errorToThrow { throw errorToThrow }
    }
}

private final class AuthProviderMutableFake: AuthProviderProtocol {
    var userId: String? = "test-user-id"
    var isAnonymous = false
    var displayName: String?
    var providerDisplayName: String?
    var linkedProviderKinds: Set<AuthProviderKind> = []

    func currentSessionProviderKind() async -> AuthProviderKind? { nil }
    func currentAuthenticationDate() async -> Date? { .now }
}

private struct SignInUseCaseStub: SignInWithAppleUseCaseProtocol, SignInWithGoogleUseCaseProtocol {
    let onCall: () -> Void

    func callAsFunction() async throws { onCall() }
}
