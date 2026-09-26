//
//  DeleteAccountUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

enum DeleteAccountError: Error {
    case requiresRecentLogin(dataAlreadyDeleted: Bool)
}

protocol DeleteAccountUseCaseProtocol {
    func callAsFunction(skipDataWipe: Bool) async throws
}

struct DeleteAccountUseCase: DeleteAccountUseCaseProtocol {

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

    func callAsFunction(skipDataWipe: Bool = false) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }

        if
            let lastSignInDate = authProvider.lastSignInDate,
            Date().timeIntervalSince(lastSignInDate) > Constants.Auth.recentLoginThreshold
        {
            throw DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: false)
        }

        // A snapshot left by a failed merge would otherwise be resumed into the fresh anonymous
        // account that follows the deletion. Idempotent, so a retry with skipDataWipe is safe.
        try snapshotStore.delete()

        if !skipDataWipe {
            try await wipeFirestoreData(userId: userId)
        }

        do {
            try await authCommandProvider.deleteCurrentUser()
        } catch {
            guard error.matches(domain: AuthErrorDomain, code: AuthErrorCode.requiresRecentLogin.rawValue) else { throw error }
            throw DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: true)
        }
    }

    private func wipeFirestoreData(userId: String) async throws {
        let mealTypes: [MealTypeDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.mealTypes(userId: userId))
        for dto in mealTypes {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.mealTypes(userId: userId))
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

        let foodItemPortions: [FoodItemPersonalPortionsDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.foodItemPortions(userId: userId))
        for dto in foodItemPortions {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.foodItemPortions(userId: userId))
        }

        let submissions: [FoodItemSubmissionDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItemSubmissions,
            where: "submitted_by",
            isEqualTo: userId,
            orderBy: "submitted_at",
            descending: false
        )
        for dto in submissions {
            try await dataProvider.deleteAsync(id: dto.id, from: Constants.Firestore.foodItemSubmissions)
        }

        let reports: [FoodItemReportDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItemReports,
            where: "reported_by",
            isEqualTo: userId,
            orderBy: "reported_at",
            descending: false
        )
        for dto in reports {
            try await dataProvider.deleteAsync(
                id: FoodItemReportDomain.id(barcode: dto.barcode, userId: userId),
                from: Constants.Firestore.foodItemReports
            )
        }

        do {
            try await dataProvider.deleteAsync(id: userId, from: Constants.Firestore.users)
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
        }
    }
}

#if DEBUG
struct DeleteAccountUseCaseFake: DeleteAccountUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(skipDataWipe: Bool = false) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
