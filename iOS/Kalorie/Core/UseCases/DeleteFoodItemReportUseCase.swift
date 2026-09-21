//
//  DeleteFoodItemReportUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

protocol DeleteFoodItemReportUseCaseProtocol {
    func callAsFunction(barcode: String, reportedBy: String) async throws
}

struct DeleteFoodItemReportUseCase: DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(barcode: String, reportedBy: String) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        try await dataProvider.deleteAsync(
            id: FoodItemReportDomain.id(barcode: barcode, userId: reportedBy),
            from: Constants.Firestore.foodItemReports
        )
    }
}

#if DEBUG
struct DeleteFoodItemReportUseCaseFake: DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(barcode: String, reportedBy: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
