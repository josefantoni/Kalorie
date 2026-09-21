//
//  SignOutUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation

protocol SignOutUseCaseProtocol {
    func callAsFunction() throws
}

struct SignOutUseCase: SignOutUseCaseProtocol {

    // MARK: - Properties

    private let authCommandProvider: any AuthCommandProviderProtocol
    private let snapshotStore: any PendingMergeSnapshotStoreProtocol
    private let googleSessionProvider: any GoogleSessionProviderProtocol

    // MARK: - Init

    init(
        authCommandProvider: any AuthCommandProviderProtocol,
        snapshotStore: any PendingMergeSnapshotStoreProtocol,
        googleSessionProvider: any GoogleSessionProviderProtocol
    ) {
        self.authCommandProvider = authCommandProvider
        self.snapshotStore = snapshotStore
        self.googleSessionProvider = googleSessionProvider
    }

    // MARK: - Functions

    // The snapshot is dropped before signing out: it is only ever valid for the account the
    // merge was destined for, and signing out immediately provisions a fresh anonymous uid.
    // A snapshot surviving into that new account would be resumed against it on the next
    // launch and write the previous user's food there.
    func callAsFunction() throws {
        try snapshotStore.delete()
        try authCommandProvider.signOut()
        googleSessionProvider.clearSession()
    }
}

#if DEBUG
struct SignOutUseCaseFake: SignOutUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
