//
//  ReauthenticateUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 01.09.2026.
//

import XCTest
@testable import Kalorie

final class ReauthenticateUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenLinkedWithApple_reauthenticatesThroughApple() async throws {
        let (sut, authCommandProvider) = makeSUT(linkedProviderKind: .apple)

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenLinkedWithGoogle_reauthenticatesThroughGoogle() async throws {
        let (sut, authCommandProvider) = makeSUT(linkedProviderKind: .google)

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenNoProviderLinked_throwsNotAuthenticatedAndCallsNothing() async {
        let (sut, authCommandProvider) = makeSUT(linkedProviderKind: nil)

        do {
            try await sut()
            XCTFail("Expected AuthError.notAuthenticated to be thrown")
        } catch AuthError.notAuthenticated {
            XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 0, "there is nothing to reauthenticate without a linked provider")
        } catch {
            XCTFail("Expected AuthError.notAuthenticated but got \(error)")
        }
    }

    func test_callAsFunction_whenAppleSignInFails_propagatesErrorWithoutReauthenticating() async {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKind: .apple,
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress)
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch AppleSignInError.signInAlreadyInProgress {
            XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 0, "reauthenticate must not be called without a fresh credential")
        } catch {
            XCTFail("Expected AppleSignInError.signInAlreadyInProgress but got \(error)")
        }
    }

    func test_callAsFunction_whenGoogleSignInFails_propagatesErrorWithoutReauthenticating() async {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKind: .google,
            googleSignInProvider: GoogleSignInProviderFake(errorToThrow: GoogleSignInError.missingIDToken)
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch GoogleSignInError.missingIDToken {
            XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 0, "reauthenticate must not be called without a fresh credential")
        } catch {
            XCTFail("Expected GoogleSignInError.missingIDToken but got \(error)")
        }
    }

    func test_callAsFunction_whenReauthenticateFails_propagatesError() async {
        let authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.reauthenticateError = URLError(.notConnectedToInternet)
        let sut = ReauthenticateUseCase(
            appleSignInProvider: AppleSignInProviderFake(),
            googleSignInProvider: GoogleSignInProviderFake(),
            authCommandProvider: authCommandProvider,
            authProvider: AuthProviderFake(linkedProviderKind: .apple)
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch is URLError {
        } catch {
            XCTFail("Expected URLError but got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        linkedProviderKind: AuthProviderKind?,
        appleSignInProvider: any AppleSignInProviderProtocol = AppleSignInProviderFake(),
        googleSignInProvider: any GoogleSignInProviderProtocol = GoogleSignInProviderFake()
    ) -> (sut: ReauthenticateUseCase, authCommandProvider: AuthCommandProviderFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let sut = ReauthenticateUseCase(
            appleSignInProvider: appleSignInProvider,
            googleSignInProvider: googleSignInProvider,
            authCommandProvider: authCommandProvider,
            authProvider: AuthProviderFake(linkedProviderKind: linkedProviderKind)
        )
        return (sut, authCommandProvider)
    }
}
