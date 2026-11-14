//
//  WithdrawUserNotificationUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import FirebaseFirestore
import Foundation

enum WithdrawUserNotificationError: Error, Equatable {
    case alreadyResolved
}

protocol WithdrawUserNotificationUseCaseProtocol {
    func callAsFunction(_ notification: UserNotificationDomain) async throws
}

struct WithdrawUserNotificationUseCase: WithdrawUserNotificationUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        guard notification.status == .pending else { throw WithdrawUserNotificationError.alreadyResolved }
        do {
            switch notification.kind {
            case .report:
                try await dataProvider.deleteAsync(id: notification.id, from: Constants.Firestore.foodItemReports)
            case .photoSuggestion:
                if let photoURL = notification.photoURL {
                    try await PhotoSuggestionRemover(storageProvider: storageProvider).deleteFile(at: photoURL)
                }
                try await dataProvider.deleteAsync(id: notification.id, from: Constants.Firestore.photoSuggestions)
            }
        } catch where error.matches(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue) {
            throw WithdrawUserNotificationError.alreadyResolved
        }
    }
}

#if DEBUG
struct WithdrawUserNotificationUseCaseFake: WithdrawUserNotificationUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
