//
//  FetchMyFoodItemReportUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

protocol FetchMyFoodItemReportUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> FoodItemReportDomain?
}

struct FetchMyFoodItemReportUseCase: FetchMyFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemReportDomain? {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto: FoodItemReportDTO? = try await dataProvider.loadAsync(
            id: FoodItemReportDomain.id(barcode: barcode, userId: userId),
            from: Constants.Firestore.foodItemReports
        )
        return dto?.asDomain()
    }
}

#if DEBUG
struct FetchMyFoodItemReportUseCaseFake: FetchMyFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    var stubbedReport: FoodItemReportDomain?
    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemReportDomain? {
        if let errorToThrow { throw errorToThrow }
        return stubbedReport
    }
}
#endif
