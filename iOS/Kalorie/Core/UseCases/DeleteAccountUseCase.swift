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
    case missingAppleAuthorizationCode
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
    private let storageProvider: any StorageDataProviderProtocol
    private let appleSignInProvider: any AppleSignInProviderProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        snapshotStore: any PendingMergeSnapshotStoreProtocol,
        storageProvider: any StorageDataProviderProtocol,
        appleSignInProvider: any AppleSignInProviderProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
        self.snapshotStore = snapshotStore
        self.storageProvider = storageProvider
        self.appleSignInProvider = appleSignInProvider
    }

    // MARK: - Functions

    func callAsFunction(skipDataWipe: Bool = false) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }

        let hasApple = authProvider.linkedProviderKinds.contains(.apple)
        let appleAuthorizationCode = hasApple ? try await reauthenticateWithApple() : nil

        if
            !hasApple,
            let authenticationDate = await authProvider.currentAuthenticationDate(),
            Date().timeIntervalSince(authenticationDate) > Constants.Auth.recentLoginThreshold
        {
            throw DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: false)
        }

        // A snapshot left by a failed merge would otherwise be resumed into the fresh anonymous
        // account that follows the deletion. Idempotent, so a retry with skipDataWipe is safe.
        try snapshotStore.delete()

        if !skipDataWipe {
            try await wipeFirestoreData(userId: userId)
        }

        if let appleAuthorizationCode {
            do {
                try await authCommandProvider.revokeAppleToken(authorizationCode: appleAuthorizationCode)
            } catch {
                Log.error(error, category: Constants.LogCategory.account)
            }
        }

        do {
            try await authCommandProvider.deleteCurrentUser()
        } catch {
            guard error.matches(domain: AuthErrorDomain, code: AuthErrorCode.requiresRecentLogin.rawValue) else { throw error }
            throw DeleteAccountError.requiresRecentLogin(dataAlreadyDeleted: true)
        }
    }

    private func reauthenticateWithApple() async throws -> String? {
        let result = try await appleSignInProvider.signIn()
        do {
            try await authCommandProvider.reauthenticate(with: result.credential)
        } catch {
            if error.matches(domain: AuthErrorDomain, code: AuthErrorCode.userMismatch.rawValue) {
                throw ReauthenticateError.wrongAccount
            }
            throw error
        }
        if result.authorizationCode == nil {
            Log.error(DeleteAccountError.missingAppleAuthorizationCode, category: Constants.LogCategory.account)
        }
        return result.authorizationCode
    }

    private func wipeBodyWeightData(userId: String) async throws {
        let bodyWeightIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.bodyWeights(userId: userId))
        try await dataProvider.batchDeleteAsync(
            ids: bodyWeightIds,
            from: Constants.Firestore.bodyWeights(userId: userId)
        )
        try await dataProvider.deleteAsync(
            id: Constants.Firestore.appSettingsDocumentId,
            from: Constants.Firestore.settings(userId: userId)
        )
    }

    private func wipeFirestoreData(userId: String) async throws {
        let mealTypesIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.mealTypes(userId: userId))
        try await dataProvider.batchDeleteAsync(ids: mealTypesIds, from: Constants.Firestore.mealTypes(userId: userId))

        let foodsIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.foodConsumed(userId: userId))
        try await dataProvider.batchDeleteAsync(ids: foodsIds, from: Constants.Firestore.foodConsumed(userId: userId))

        let favouriteFoodsIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.favouriteFoods(userId: userId))
        try await dataProvider.batchDeleteAsync(ids: favouriteFoodsIds, from: Constants.Firestore.favouriteFoods(userId: userId))

        let myCreatedMealsIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.myCreatedMeals(userId: userId))
        try await dataProvider.batchDeleteAsync(ids: myCreatedMealsIds, from: Constants.Firestore.myCreatedMeals(userId: userId))

        let foodItemPortionsIds = try await dataProvider.loadDocumentIdsAsync(from: Constants.Firestore.foodItemPortions(userId: userId))
        try await dataProvider.batchDeleteAsync(ids: foodItemPortionsIds, from: Constants.Firestore.foodItemPortions(userId: userId))

        try await wipeBodyWeightData(userId: userId)

        try await dataProvider.deleteAsync(
            id: Constants.Firestore.foodFrequencyDocumentId,
            from: Constants.Firestore.stats(userId: userId)
        )

        let submissionIds = try await dataProvider.loadDocumentIdsAsync(
            from: Constants.Firestore.foodItemSubmissions,
            where: Constants.Firestore.Field.submittedBy,
            isEqualTo: userId
        )
        try await dataProvider.batchDeleteAsync(ids: submissionIds, from: Constants.Firestore.foodItemSubmissions)

        // Listing the folder, rather than following the deleted submissions' URLs, also removes
        // orphans left by earlier failed writes. catalogPhotos is shared catalogue content and stays.
        let photoURLs = try await storageProvider.listAsync(prefix: "\(Constants.Storage.submissionPhotosFolder)/\(userId)")
        for url in photoURLs {
            try await storageProvider.deleteAsync(url: url)
        }

        let reportIds = try await dataProvider.loadDocumentIdsAsync(
            from: Constants.Firestore.foodItemReports,
            where: Constants.Firestore.Field.reportedBy,
            isEqualTo: userId
        )
        try await dataProvider.batchDeleteAsync(ids: reportIds, from: Constants.Firestore.foodItemReports)

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
