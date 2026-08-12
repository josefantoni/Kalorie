//
//  IsFavouriteFoodUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

protocol IsFavouriteFoodUseCaseProtocol {
    func callAsFunction(id: String) async throws -> Bool
}

struct IsFavouriteFoodUseCase: IsFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(id: String) async throws -> Bool {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto: FavouriteFoodDTO? = try await dataProvider.loadAsync(
            from: Constants.Firestore.favouriteFoods(userId: userId),
            where: "id",
            isEqualTo: id
        )
        return dto != nil
    }
}

#if DEBUG
struct IsFavouriteFoodUseCaseFake: IsFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    var stubbedResult = false
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(id: String) async throws -> Bool {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedResult
    }
}
#endif
