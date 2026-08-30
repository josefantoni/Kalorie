//
//  DeleteAccountUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

enum DeleteAccountError: Error {
    case requiresRecentLogin
}

protocol DeleteAccountUseCaseProtocol {
    func callAsFunction() async throws
}

struct DeleteAccountUseCase: DeleteAccountUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }

        if
            let lastSignInDate = authProvider.lastSignInDate,
            Date().timeIntervalSince(lastSignInDate) > Constants.Auth.recentLoginThreshold
        {
            throw DeleteAccountError.requiresRecentLogin
        }

        let mealTypes: [MealTypeDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.mealTypes(userId: userId))
        for dto in mealTypes {
            try await dataProvider.deleteAsync(id: "\(dto.id)", from: Constants.Firestore.mealTypes(userId: userId))
        }

        let foods: [FoodConsumedDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.foodConsumed(userId: userId))
        for dto in foods {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.foodConsumed(userId: userId))
        }

        let favouriteFoods: [FavouriteFoodDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.favouriteFoods(userId: userId))
        for dto in favouriteFoods {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.favouriteFoods(userId: userId))
        }

        let myCreatedMeals: [MyCreatedMealDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.myCreatedMeals(userId: userId))
        for dto in myCreatedMeals {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.myCreatedMeals(userId: userId))
        }

        try? await dataProvider.deleteAsync(id: userId, from: Constants.Firestore.users)

        do {
            try await authCommandProvider.deleteCurrentUser()
        } catch {
            guard
                let nsError = error as NSError?,
                nsError.code == AuthErrorCode.requiresRecentLogin.rawValue
            else { throw error }
            throw DeleteAccountError.requiresRecentLogin
        }
    }
}

#if DEBUG
struct DeleteAccountUseCaseFake: DeleteAccountUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
