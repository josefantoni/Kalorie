//
//  LinkOrMergeCredentialUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

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
        let anonymousUserId = authProvider.userId
        do {
            try await authCommandProvider.link(with: credential)
        } catch {
            guard
                let anonymousUserId,
                let nsError = error as NSError?,
                nsError.code == AuthErrorCode.credentialAlreadyInUse.rawValue
            else { throw error }

            try await migrateAnonymousData.migrate(fromAnonymousUserId: anonymousUserId, credential: credential)
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
