//
//  ReauthenticateUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 01.09.2026.
//

import FirebaseAuth
import XCTest
@testable import Kalorie

final class ReauthenticateUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenLinkedWithApple_reauthenticatesThroughApple() async throws {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds: [.apple],
            googleSignInProvider: GoogleSignInProviderFake(errorToThrow: GoogleSignInError.missingIDToken)
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenLinkedWithGoogle_reauthenticatesThroughGoogle() async throws {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds: [.google],
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress)
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenNoProviderLinked_throwsNotAuthenticatedAndCallsNothing() async {
        let (sut, authCommandProvider) = makeSUT(linkedProviderKinds: [])

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
            linkedProviderKinds: [.apple],
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
            linkedProviderKinds: [.google],
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
            authProvider: AuthProviderFake(linkedProviderKinds: [.apple])
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch is URLError {
        } catch {
            XCTFail("Expected URLError but got \(error)")
        }
    }

    func test_callAsFunction_whenBothProvidersLinked_usesTheProviderOfTheCurrentSession() async throws {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds: [.apple, .google],
            sessionProviderKind: .google,
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress)
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1, "the session provider is always valid, so the user is never asked to pick one")
    }

    func test_callAsFunction_whenSessionProviderIsUnknownAndOneIsLinked_fallsBackToTheLinkedOne() async throws {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds: [.google],
            sessionProviderKind: nil,
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress)
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenSessionProviderIsNotLinkedAndOneIsLinked_fallsBackToTheLinkedOne() async throws {
        let (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds: [.apple],
            sessionProviderKind: .google,
            googleSignInProvider: GoogleSignInProviderFake(errorToThrow: GoogleSignInError.missingIDToken)
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 1)
    }

    func test_callAsFunction_whenSessionProviderIsUnknownAndBothAreLinked_throwsNotAuthenticated() async {
        let (sut, authCommandProvider) = makeSUT(linkedProviderKinds: [.apple, .google], sessionProviderKind: nil)

        do {
            try await sut()
            XCTFail("Expected AuthError.notAuthenticated to be thrown")
        } catch AuthError.notAuthenticated {
            XCTAssertEqual(authCommandProvider.reauthenticateCallCount, 0, "guessing a provider could ask the user to sign in with the wrong identity in a destructive flow")
        } catch {
            XCTFail("Expected AuthError.notAuthenticated but got \(error)")
        }
    }

    func test_callAsFunction_whenUserPicksADifferentIdentity_throwsWrongAccount() async {
        let authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.reauthenticateError = NSError(domain: AuthErrorDomain, code: AuthErrorCode.userMismatch.rawValue)
        let sut = ReauthenticateUseCase(
            appleSignInProvider: AppleSignInProviderFake(),
            googleSignInProvider: GoogleSignInProviderFake(),
            authCommandProvider: authCommandProvider,
            authProvider: AuthProviderFake(linkedProviderKinds: [.apple])
        )

        do {
            try await sut()
            XCTFail("Expected ReauthenticateError.wrongAccount to be thrown")
        } catch ReauthenticateError.wrongAccount {
        } catch {
            XCTFail("Expected ReauthenticateError.wrongAccount but got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        linkedProviderKinds: Set<AuthProviderKind>,
        sessionProviderKind: AuthProviderKind? = nil,
        appleSignInProvider: any AppleSignInProviderProtocol = AppleSignInProviderFake(),
        googleSignInProvider: any GoogleSignInProviderProtocol = GoogleSignInProviderFake()
    ) -> (sut: ReauthenticateUseCase, authCommandProvider: AuthCommandProviderFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let sut = ReauthenticateUseCase(
            appleSignInProvider: appleSignInProvider,
            googleSignInProvider: googleSignInProvider,
            authCommandProvider: authCommandProvider,
            authProvider: AuthProviderFake(linkedProviderKinds: linkedProviderKinds, sessionProviderKind: sessionProviderKind)
        )
        return (sut, authCommandProvider)
    }
}
