//
//  ReauthenticateUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 01.09.2026.
//

import FirebaseAuth
import Foundation

enum ReauthenticateError: Error {
    case wrongAccount
}

protocol ReauthenticateUseCaseProtocol {
    func callAsFunction() async throws
}

struct ReauthenticateUseCase: ReauthenticateUseCaseProtocol {

    // MARK: - Properties

    private let appleSignInProvider: any AppleSignInProviderProtocol
    private let googleSignInProvider: any GoogleSignInProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(
        appleSignInProvider: any AppleSignInProviderProtocol,
        googleSignInProvider: any GoogleSignInProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        authProvider: any AuthProviderProtocol
    ) {
        self.appleSignInProvider = appleSignInProvider
        self.googleSignInProvider = googleSignInProvider
        self.authCommandProvider = authCommandProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let credential: AuthCredential
        switch try await providerKind() {
        case .apple:
            credential = try await appleSignInProvider.signIn().credential
        case .google:
            credential = try await googleSignInProvider.signIn().credential
        }

        do {
            try await authCommandProvider.reauthenticate(with: credential)
        } catch {
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.userMismatch.rawValue) {
                throw ReauthenticateError.wrongAccount
            }
            throw error
        }
    }

    private func providerKind() async throws -> AuthProviderKind {
        let linkedKinds = authProvider.linkedProviderKinds
        if
            let sessionKind = await authProvider.currentSessionProviderKind(),
            linkedKinds.contains(sessionKind)
        {
            return sessionKind
        }
        guard
            linkedKinds.count == 1,
            let onlyKind = linkedKinds.first
        else { throw AuthError.notAuthenticated }
        return onlyKind
    }
}

#if DEBUG
struct ReauthenticateUseCaseFake: ReauthenticateUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
