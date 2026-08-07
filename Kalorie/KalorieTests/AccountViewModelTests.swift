//
//  AccountViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
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

    // MARK: - Helpers

    private func makeSUT(
        authProvider: any AuthProviderProtocol = AuthProviderFake(),
        signOut: any SignOutUseCaseProtocol = SignOutUseCaseFake(),
        deleteAccount: any DeleteAccountUseCaseProtocol = DeleteAccountUseCaseFake(),
        mergeStatusReporting: any MergeStatusReporting = MergeStatusReportingFake()
    ) -> AccountViewModel {
        let sut = AccountViewModel(
            authProvider: authProvider,
            signOut: signOut,
            signInWithApple: SignInWithAppleUseCaseFake(),
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
}
