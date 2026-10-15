//
//  LinkCredentialUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import FirebaseAuth
import Foundation

enum LinkCredentialError: Error {
    case alreadyLinked
    case credentialBelongsToAnotherAccount
    case emailBelongsToAnotherAccount
    case mergeInProgress
}

protocol LinkCredentialUseCaseProtocol {
    func callAsFunction(credential: AuthCredential) async throws
}

struct LinkCredentialUseCase: LinkCredentialUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let snapshotStore: any PendingMergeSnapshotStoreProtocol

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        snapshotStore: any PendingMergeSnapshotStoreProtocol
    ) {
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
        self.snapshotStore = snapshotStore
    }

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        guard !authProvider.isAnonymous else { throw AuthError.notAuthenticated }
        guard try snapshotStore.load() == nil else { throw LinkCredentialError.mergeInProgress }

        do {
            try await authCommandProvider.link(with: credential)
        } catch {
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.providerAlreadyLinked.rawValue) {
                throw LinkCredentialError.alreadyLinked
            }
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.credentialAlreadyInUse.rawValue) {
                throw LinkCredentialError.credentialBelongsToAnotherAccount
            }
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.emailAlreadyInUse.rawValue) {
                throw LinkCredentialError.emailBelongsToAnotherAccount
            }
            throw error
        }
    }
}

#if DEBUG
struct LinkCredentialUseCaseFake: LinkCredentialUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
