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

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
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
        let dto = FoodItemReportDTO(barcode: barcode, reportedBy: userId, reason: trimmedReason, reportedAt: .now)
        try await dataProvider.setAsync(
            dto,
            id: FoodItemReportDomain.id(barcode: barcode, userId: userId),
            in: Constants.Firestore.foodItemReports
        )
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
