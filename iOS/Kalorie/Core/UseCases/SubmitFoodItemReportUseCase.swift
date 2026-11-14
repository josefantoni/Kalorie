//
//  SubmitFoodItemReportUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

protocol SubmitFoodItemReportUseCaseProtocol {
    func callAsFunction(barcode: String, reason: String) async throws
}

struct SubmitFoodItemReportUseCase: SubmitFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let notificationsStore: any UserNotificationsStoreProtocol
    private let locale: String

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        notificationsStore: any UserNotificationsStoreProtocol,
        locale: String = TicketLocale.current()
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.notificationsStore = notificationsStore
        self.locale = locale
    }

    // MARK: - Functions

    func callAsFunction(barcode: String, reason: String) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let trimmedReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedReason.isEmpty else { throw FoodItemReportError.reasonRequired }
        // Rules' string.size() counts UTF-16 code units — an emoji is one Character but two or more units.
        guard trimmedReason.utf16.count <= Constants.Firestore.reportReasonMaxLength else {
            throw FoodItemReportError.reasonTooLong
        }
        let dto = FoodItemReportCreateDTO(
            barcode: barcode,
            reportedBy: userId,
            reason: trimmedReason,
            reportedAt: .now,
            locale: locale
        )
        let ticketId = try dataProvider.newDocumentId(in: Constants.Firestore.foodItemReports)
        try await dataProvider.setAsync(dto, id: ticketId, in: Constants.Firestore.foodItemReports)
        notificationsStore.mayHaveUnread = true
        notificationsStore.lastSubmittedAt = .now
    }
}

#if DEBUG
struct SubmitFoodItemReportUseCaseFake: SubmitFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(barcode: String, reason: String) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
