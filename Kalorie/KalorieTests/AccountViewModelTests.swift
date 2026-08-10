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
    func test_onDeleteAccountConfirmed_whenRequiresRecentLogin_showsSpecificAlert() async {
        let sut = makeSUT(deleteAccount: DeleteAccountUseCaseFake(errorToThrow: DeleteAccountError.requiresRecentLogin))
        await sut.onDeleteAccountConfirmed()
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorDeleteRequiresRecentLogin)
    }

    @MainActor
    func test_onDeleteAccountConfirmed_whenFailsWithOtherError_showsGenericAlert() async {
        let sut = makeSUT(deleteAccount: DeleteAccountUseCaseFake(errorToThrow: URLError(.unknown)))
        await sut.onDeleteAccountConfirmed()
        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorDeleteFailed)
    }

    @MainActor
    func test_onSignInCompletion_whenCancelled_showsNoAlert() async {
        let sut = makeSUT()

        await sut.onSignInCompletion(.failure(makeAuthorizationError(.canceled)))

        XCTAssertNil(sut.alertItem, "uživatel zrušil dialog sám — nejde o chybu, kterou je třeba hlásit")
    }

    @MainActor
    func test_onSignInCompletion_whenFailsWithOtherError_showsAlert() async {
        let sut = makeSUT()

        await sut.onSignInCompletion(.failure(makeAuthorizationError(.failed)))

        XCTAssertEqual(sut.alertItem?.title, L10n.Account.errorSignInFailed)
    }

    @MainActor
    func test_onSignInCompletion_whenCancelled_doesNotReportMerge() async {
        let mergeStatusReporting = MergeStatusReportingFake()
        let sut = makeSUT(mergeStatusReporting: mergeStatusReporting)

        await sut.onSignInCompletion(.failure(makeAuthorizationError(.canceled)))

        XCTAssertEqual(mergeStatusReporting.beginMergeCallCount, 0, "zrušený dialog nikdy nedosáhl signInWithApple, není co reportovat")
        XCTAssertEqual(mergeStatusReporting.endMergeCallCount, 0)
    }

    @MainActor
    func test_onSignInWithGoogleTapped_whenSucceeds_showsNoAlertAndReturnsToIdle() async {
        let sut = makeSUT()

        await sut.onSignInWithGoogleTapped()

        XCTAssertNil(sut.alertItem)
        XCTAssertEqual(sut.state, .idle)
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

    // MARK: - Helpers

    private func makeSUT(
        authProvider: any AuthProviderProtocol = AuthProviderFake(),
        signOut: any SignOutUseCaseProtocol = SignOutUseCaseFake(),
        signInWithGoogle: any SignInWithGoogleUseCaseProtocol = SignInWithGoogleUseCaseFake(),
        deleteAccount: any DeleteAccountUseCaseProtocol = DeleteAccountUseCaseFake(),
        mergeStatusReporting: any MergeStatusReporting = MergeStatusReportingFake()
    ) -> AccountViewModel {
        let sut = AccountViewModel(
            authProvider: authProvider,
            signOut: signOut,
            signInWithApple: SignInWithAppleUseCaseFake(),
            signInWithGoogle: signInWithGoogle,
            deleteAccount: deleteAccount,
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
