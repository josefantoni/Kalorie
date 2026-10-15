//
//  LinkOrMergeCredentialUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

enum LinkOrMergeCredentialError: Error {
    case accountExistsWithAnotherProvider
    case notAnonymous
}

protocol LinkOrMergeCredentialUseCaseProtocol {
    func callAsFunction(credential: AuthCredential) async throws
}

struct LinkOrMergeCredentialUseCase: LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let migrateAnonymousData: any MigrateAnonymousDataUseCaseProtocol

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        migrateAnonymousData: any MigrateAnonymousDataUseCaseProtocol
    ) {
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
        self.migrateAnonymousData = migrateAnonymousData
    }

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        guard authProvider.isAnonymous else { throw LinkOrMergeCredentialError.notAnonymous }
        let anonymousUserId = authProvider.userId
        do {
            try await authCommandProvider.link(with: credential)
        } catch {
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.emailAlreadyInUse.rawValue) {
                throw LinkOrMergeCredentialError.accountExistsWithAnotherProvider
            }

            guard
                let anonymousUserId,
                error.matches(domain: AuthErrorDomain, code: AuthErrorCode.credentialAlreadyInUse.rawValue)
            else { throw error }

            // An Apple ID token is single-use: the credential that just failed to link is spent, and Firebase hands back a usable one.
            let updatedCredential = (error as NSError).userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential
            try await migrateAnonymousData.migrate(fromAnonymousUserId: anonymousUserId, credential: updatedCredential ?? credential)
        }
    }
}

#if DEBUG
struct LinkOrMergeCredentialUseCaseFake: LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
