//
//  MigrateAnonymousDataUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

protocol MigrateAnonymousDataUseCaseProtocol {
    func migrate(fromAnonymousUserId sourceUserId: String, credential: AuthCredential) async throws
    func resumeIfNeeded() async throws
}

struct MigrateAnonymousDataUseCase: MigrateAnonymousDataUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let snapshotStore: any PendingMergeSnapshotStoreProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        snapshotStore: any PendingMergeSnapshotStoreProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
        self.snapshotStore = snapshotStore
    }

    // MARK: - Functions

    func migrate(fromAnonymousUserId sourceUserId: String, credential: AuthCredential) async throws {
        let foodConsumed: [FoodConsumedDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.foodConsumed(userId: sourceUserId))
        let favouriteFoods: [FavouriteFoodDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.favouriteFoods(userId: sourceUserId))
        let snapshot = PendingMergeSnapshot(sourceAnonymousUserId: sourceUserId, foodConsumed: foodConsumed, favouriteFoods: favouriteFoods)
        try snapshotStore.save(snapshot)

        try await authCommandProvider.signIn(with: credential)

        try await writeAndCleanup(snapshot)
    }

    func resumeIfNeeded() async throws {
        guard let snapshot = try snapshotStore.load() else { return }
        guard let currentUserId = authProvider.userId else { return }

        if currentUserId == snapshot.sourceAnonymousUserId {
            try snapshotStore.delete()
        } else {
            try await writeAndCleanup(snapshot)
        }
    }

    // MARK: - Private

    private func writeAndCleanup(_ snapshot: PendingMergeSnapshot) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let items = snapshot.foodConsumed.map { (item: $0, id: $0.id) }
        try await dataProvider.batchSetAsync(items, in: Constants.Firestore.foodConsumed(userId: userId))
        let favourites = snapshot.favouriteFoods.map { (item: $0, id: $0.id) }
        try await dataProvider.batchSetAsync(favourites, in: Constants.Firestore.favouriteFoods(userId: userId))
        try snapshotStore.delete()
    }
}

#if DEBUG
final class MigrateAnonymousDataUseCaseFake: MigrateAnonymousDataUseCaseProtocol {

    // MARK: - Properties

    var migrateError: Error?
    var resumeError: Error?
    private(set) var migrateCallCount = 0
    private(set) var lastSourceUserId: String?
    private(set) var resumeCallCount = 0

    // MARK: - Functions

    func migrate(fromAnonymousUserId sourceUserId: String, credential: AuthCredential) async throws {
        migrateCallCount += 1
        lastSourceUserId = sourceUserId
        if let migrateError { throw migrateError }
    }

    func resumeIfNeeded() async throws {
        resumeCallCount += 1
        if let resumeError { throw resumeError }
    }
}
#endif
