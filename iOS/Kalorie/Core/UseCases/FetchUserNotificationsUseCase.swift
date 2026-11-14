//
//  FetchUserNotificationsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

protocol FetchUserNotificationsUseCaseProtocol {
    func callAsFunction() async throws -> [UserNotificationDomain]
}

struct FetchUserNotificationsUseCase: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        async let reports: [FoodItemReportDTO] = dataProvider.loadAsync(
            from: Constants.Firestore.foodItemReports,
            whereEqualTo: [(Constants.Firestore.Field.reportedBy, userId)],
            isHidden: false,
            orderBy: Constants.Firestore.Field.reportedAt,
            descending: true
        )
        async let suggestions: [PhotoSuggestionDTO] = dataProvider.loadAsync(
            from: Constants.Firestore.photoSuggestions,
            whereEqualTo: [(Constants.Firestore.Field.suggestedBy, userId)],
            isHidden: false,
            orderBy: Constants.Firestore.Field.suggestedAt,
            descending: true
        )
        let (fetchedReports, fetchedSuggestions) = try await (reports, suggestions)
        let reportNotifications = fetchedReports.compactMap { $0.asDomain() }.map {
            UserNotificationDomain(
                kind: .report,
                id: $0.id,
                itemId: $0.barcode,
                createdAt: $0.reportedAt,
                status: $0.status,
                response: $0.response,
                resolvedAt: $0.resolvedAt,
                seenAt: $0.seenAt,
                reason: $0.reason
            )
        }
        let suggestionNotifications = fetchedSuggestions.compactMap { $0.asDomain() }.map {
            UserNotificationDomain(
                kind: .photoSuggestion,
                id: $0.id,
                itemId: $0.itemId,
                createdAt: $0.suggestedAt,
                status: $0.status,
                response: $0.response,
                resolvedAt: $0.resolvedAt,
                seenAt: $0.seenAt,
                photoURL: $0.status == .pending ? $0.photoURL : nil
            )
        }
        return (reportNotifications + suggestionNotifications).sorted { $0.lastEventAt > $1.lastEventAt }
    }
}

#if DEBUG
struct FetchUserNotificationsUseCaseFake: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    var stubbedNotifications: [UserNotificationDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedNotifications
    }
}
#endif
