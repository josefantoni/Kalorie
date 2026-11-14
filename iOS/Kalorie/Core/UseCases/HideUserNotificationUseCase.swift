//
//  HideUserNotificationUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

protocol HideUserNotificationUseCaseProtocol {
    func callAsFunction(_ notification: UserNotificationDomain) async throws
}

struct HideUserNotificationUseCase: HideUserNotificationUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        try await dataProvider.updateAsync(
            NotificationHiddenDTO(),
            id: notification.id,
            in: notification.kind.collection
        )
    }
}

#if DEBUG
struct HideUserNotificationUseCaseFake: HideUserNotificationUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
