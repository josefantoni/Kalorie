//
//  DeleteAccountUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import XCTest
import FirebaseAuth
@testable import Kalorie

final class DeleteAccountUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_deletesFavouriteFoodsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedFavouriteFoods = [makeFavourite(id: "fav1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("deleteFavourite:fav1"), "Firestore does not cascade — favourites are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesMyCreatedMealsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMyCreatedMeals = [makeMeal(id: "meal1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("deleteMeal:meal1"), "Firestore does not cascade — created meals are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesFoodItemPortionsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedFoodItemPortions = [FoodItemPersonalPortionsDTO(id: "12345678", portions: [FoodPortionDTO(portion: FoodPortionDomain(name: "1 balení", grams: 33))])]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("deletePortions:12345678"), "Firestore does not cascade — personal portions are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesBodyWeightsAndSettingsAsPrivacyObligation_beforeTheProfile() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedBodyWeights = [
            BodyWeightDTO(weight: BodyWeightDomain(dayKey: "2026-10-27", kilograms: 78.4)),
            BodyWeightDTO(weight: BodyWeightDomain(dayKey: "2026-10-26", kilograms: 78.9))
        ]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        let entries = log.entries
        XCTAssertTrue(entries.contains("batchDelete:bodyWeights:2026-10-27,2026-10-26"), "Firestore does not cascade, so body weights are wiped explicitly, in one batch")
        XCTAssertFalse(entries.contains { $0.hasPrefix("deleteBodyWeight:") })
        XCTAssertTrue(entries.contains("deleteSettings:app"))
        let profileIndex = entries.firstIndex { $0.hasPrefix("deleteUser:") } ?? entries.count
        XCTAssertLessThan(entries.firstIndex(of: "batchDelete:bodyWeights:2026-10-27,2026-10-26") ?? .max, profileIndex)
    }

    func test_callAsFunction_keepsTheMergedAnonymousAccountMarkersSoTheCleanupScriptCanStillFindTheAbandonedAccount() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMergedAnonymousAccounts = [MergedAnonymousAccountDTO(id: "anon-1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertFalse(log.entries.contains("deleteMergedAnonymousAccount:anon-1"), "The marker is the cleanup script's only pointer to the abandoned anonymous account")
    }

    func test_callAsFunction_deletesOwnSubmissionsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedSubmissions = [makeSubmission(id: "sub1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(
            log.entries.dropLast().contains("deleteSubmission:sub1"),
            "A pending or rejected submission is the user's own data and must not survive account deletion"
        )
    }

    func test_callAsFunction_deletesOwnReportsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedReports = [FoodItemReportDTO(barcode: "12345678", reportedBy: "test-user-id", reason: "wrong calories", reportedAt: .now)]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(
            log.entries.dropLast().contains("deleteReport:12345678_test-user-id"),
            "A report is the user's own data and must not survive account deletion"
        )
    }

    func test_callAsFunction_deletesFirestoreDataBeforeDeletingAuthAccount() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
        XCTAssertTrue(log.entries.dropLast().contains("deleteMealType:0"))
        XCTAssertTrue(log.entries.dropLast().contains("deleteFood:f1"))
    }

    func test_callAsFunction_deletesAllMealTypesAndFoodsById() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [
            MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60),
            MealTypeDTO(id: "1", name: "Oběd", startMinutes: 60, endMinutes: 120)
        ]
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1"), makeFood(id: "f2")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(Set(["0", "1", "f1", "f2"]).isSubset(of: Set(dataProvider.deletedIds)))
    }

    func test_callAsFunction_deletesFoodFrequencyDocument() async throws {
        let log = OperationLog()
        let sut = makeSUT(dataProvider: DeleteAccountDataProviderFake(log: log), log: log)

        try await sut()

        XCTAssertTrue(
            log.entries.dropLast().contains("deleteStats:foodFrequency"),
            "the per-user frequency counts are private data and must not outlive the account"
        )
    }

    func test_callAsFunction_deletesTheUsersSubmissionPhotosButNotCatalogueOrOtherUsersPhotos() async throws {
        let log = OperationLog()
        let storage = StorageDataProviderFake()
        let own = try await storage.uploadAsync(data: Data([1]), path: "submissionPhotos/test-user-id/a.jpg", contentType: "image/jpeg")
        let orphan = try await storage.uploadAsync(data: Data([2]), path: "submissionPhotos/test-user-id/orphan.jpg", contentType: "image/jpeg")
        let someoneElse = try await storage.uploadAsync(data: Data([3]), path: "submissionPhotos/other-user/b.jpg", contentType: "image/jpeg")
        let catalogue = try await storage.uploadAsync(data: Data([4]), path: "catalogPhotos/12345678/c.jpg", contentType: "image/jpeg")
        let sut = makeSUT(dataProvider: DeleteAccountDataProviderFake(log: log), log: log, storage: storage)

        try await sut()

        XCTAssertNil(storage.files[own])
        XCTAssertNil(storage.files[orphan], "orphans from failed writes are not reachable through the deleted submissions, only through the folder")
        XCTAssertNotNil(storage.files[someoneElse])
        XCTAssertNotNil(storage.files[catalogue], "approved items are shared catalogue content and outlive the account")
    }

    func test_callAsFunction_whenListingPhotosFails_doesNotDeleteTheAuthUser() async throws {
        let log = OperationLog()
        let storage = StorageDataProviderFake()
        storage.listError = URLError(.notConnectedToInternet)
        let sut = makeSUT(dataProvider: DeleteAccountDataProviderFake(log: log), log: log, storage: storage)

        _ = try? await sut()

        XCTAssertFalse(log.entries.contains("deleteCurrentUser"), "the photos hold the user's content; the account must survive so the deletion can be retried")
    }

    func test_callAsFunction_whenNotAuthenticated_throwsAuthError() async {
        let log = OperationLog()
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(userId: nil),
            authCommandProvider: DeleteAccountAuthCommandProviderFake(log: log),
            snapshotStore: PendingMergeSnapshotStoreFake(),
            storageProvider: StorageDataProviderFake(),
            appleSignInProvider: AppleSignInProviderFake()
        )

        do {
            _ = try await sut()
            XCTFail("Expected AuthError to be thrown")
        } catch is AuthError {
            // pass
        } catch {
            XCTFail("Expected AuthError but got \(error)")
        }
    }

    func test_callAsFunction_whenLastSignInIsStale_throwsBeforeDeletingAnyData() async {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let snapshotStore = PendingMergeSnapshotStoreFake()
        snapshotStore.stubbedSnapshot = makeSnapshot()
        let sut = DeleteAccountUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(authenticationDate: Date().addingTimeInterval(-10 * 60)),
            authCommandProvider: DeleteAccountAuthCommandProviderFake(log: log),
            snapshotStore: snapshotStore,
            storageProvider: StorageDataProviderFake(),
            appleSignInProvider: AppleSignInProviderFake()
        )

        do {
            try await sut()
            XCTFail("Expected DeleteAccountError.requiresRecentLogin to be thrown")
        } catch DeleteAccountError.requiresRecentLogin(let dataAlreadyDeleted) {
            XCTAssertTrue(log.entries.isEmpty, "A stale session must be rejected before any data is deleted, otherwise the account survives but its history does not")
            XCTAssertFalse(dataAlreadyDeleted, "nothing was deleted yet, so a retry must not skip the wipe")
            XCTAssertNotNil(snapshotStore.stubbedSnapshot, "a rejected attempt must leave everything intact so the user can simply retry")
        } catch {
            XCTFail("Expected requiresRecentLogin but got \(error)")
        }
    }

    func test_callAsFunction_whenRequiresRecentLogin_throwsTypedErrorFlaggingDataAlreadyDeleted() async {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.deleteError = NSError(domain: AuthErrorDomain, code: AuthErrorCode.requiresRecentLogin.rawValue)
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(),
            authCommandProvider: authCommandProvider,
            snapshotStore: PendingMergeSnapshotStoreFake(),
            storageProvider: StorageDataProviderFake(),
            appleSignInProvider: AppleSignInProviderFake()
        )

        do {
            try await sut()
            XCTFail("Expected DeleteAccountError.requiresRecentLogin to be thrown")
        } catch DeleteAccountError.requiresRecentLogin(let dataAlreadyDeleted) {
            XCTAssertTrue(dataAlreadyDeleted, "the wipe already ran by the time deleteCurrentUser() is reached, so a retry must skip it")
        } catch {
            XCTFail("Expected requiresRecentLogin but got \(error)")
        }
    }

    func test_callAsFunction_whenSkipDataWipeIsTrue_doesNotReReadAlreadyWipedCollections() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut(skipDataWipe: true)

        XCTAssertTrue(dataProvider.deletedIds.isEmpty, "a retry that already wiped the data must not re-read and re-delete it")
        XCTAssertEqual(log.entries, ["deleteCurrentUser"])
    }

    func test_callAsFunction_discardsPendingMergeSnapshot() async throws {
        let log = OperationLog()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        snapshotStore.stubbedSnapshot = makeSnapshot()
        let sut = makeSUT(dataProvider: DeleteAccountDataProviderFake(log: log), log: log, snapshotStore: snapshotStore)

        try await sut()

        XCTAssertNil(snapshotStore.stubbedSnapshot, "a snapshot surviving the deletion would be resumed into the fresh anonymous account on the next launch")
    }

    func test_callAsFunction_whenSkipDataWipeIsTrue_stillDiscardsPendingMergeSnapshot() async throws {
        let log = OperationLog()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        snapshotStore.stubbedSnapshot = makeSnapshot()
        let sut = makeSUT(dataProvider: DeleteAccountDataProviderFake(log: log), log: log, snapshotStore: snapshotStore)

        try await sut(skipDataWipe: true)

        XCTAssertNil(snapshotStore.stubbedSnapshot, "the first attempt may have failed before reaching the snapshot, so the retry must still clear it")
    }

    func test_callAsFunction_whenSnapshotDeleteFails_deletesNothingElse() async {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let snapshotStore = PendingMergeSnapshotStoreFake()
        snapshotStore.deleteError = URLError(.unknown)
        let sut = makeSUT(dataProvider: dataProvider, log: log, snapshotStore: snapshotStore)

        do {
            try await sut()
            XCTFail("Expected the snapshot error to be thrown")
        } catch {
            XCTAssertTrue(log.entries.isEmpty, "the account must survive intact when the snapshot cannot be cleared, so the user can retry")
        }
    }

    func test_callAsFunction_whenDeleteFailsWithOtherError_propagatesError() async {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.deleteError = URLError(.notConnectedToInternet)
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(),
            authCommandProvider: authCommandProvider,
            snapshotStore: PendingMergeSnapshotStoreFake(),
            storageProvider: StorageDataProviderFake(),
            appleSignInProvider: AppleSignInProviderFake()
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch DeleteAccountError.requiresRecentLogin {
            XCTFail("Did not expect requiresRecentLogin")
        } catch {
            // pass
        }
    }

    func test_callAsFunction_whenAccountHasApple_revokesTheAppleTokenBeforeDeletingTheUser() async throws {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        let sut = makeSUT(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            log: log,
            linkedProviderKinds: [.apple],
            appleSignInProvider: makeAppleSignInProvider(authorizationCode: "auth-code"),
            authCommandProvider: authCommandProvider
        )

        try await sut()

        XCTAssertEqual(authCommandProvider.revokedAuthorizationCodes, ["auth-code"])
        XCTAssertEqual(
            log.entries.suffix(2),
            ["revokeAppleToken", "deleteCurrentUser"],
            "Apple requires the token to be revoked as part of account deletion, and revoking after the user is gone is impossible"
        )
    }

    func test_callAsFunction_whenAccountHasNoApple_doesNotAskAppleForAuthorizationOrRevoke() async throws {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        let sut = makeSUT(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            log: log,
            linkedProviderKinds: [.google],
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: URLError(.unknown)),
            authCommandProvider: authCommandProvider
        )

        try await sut()

        XCTAssertTrue(authCommandProvider.revokedAuthorizationCodes.isEmpty)
        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
    }

    func test_callAsFunction_whenRevocationFails_stillDeletesTheUser() async throws {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.revokeError = URLError(.notConnectedToInternet)
        let sut = makeSUT(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            log: log,
            linkedProviderKinds: [.apple],
            appleSignInProvider: makeAppleSignInProvider(authorizationCode: "auth-code"),
            authCommandProvider: authCommandProvider
        )

        try await sut()

        XCTAssertEqual(log.entries.last, "deleteCurrentUser", "a failed revocation is only logged: the user asked to delete the account and must not be stuck with it")
    }

    func test_callAsFunction_whenAppleAuthorizationIsCancelled_deletesNothing() async {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let sut = makeSUT(
            dataProvider: dataProvider,
            log: log,
            linkedProviderKinds: [.apple],
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: ASAuthorizationError(.canceled))
        )

        do {
            try await sut()
            XCTFail("Expected the cancellation to be thrown")
        } catch {
            XCTAssertTrue(log.entries.isEmpty, "cancelling the Apple prompt must leave the account and its data intact so the user can retry")
        }
    }

    func test_callAsFunction_whenAccountHasApple_andLoginIsStale_reauthenticatesWithAppleInsteadOfAskingForAnotherLogin() async throws {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(linkedProviderKinds: [.apple], authenticationDate: Date().addingTimeInterval(-10 * 60)),
            authCommandProvider: authCommandProvider,
            snapshotStore: PendingMergeSnapshotStoreFake(),
            storageProvider: StorageDataProviderFake(),
            appleSignInProvider: makeAppleSignInProvider(authorizationCode: "auth-code")
        )

        try await sut()

        XCTAssertEqual(log.entries.first, "reauthenticate", "the Apple prompt already proves a fresh login, so a stale one must not send the user through a second prompt")
        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
    }

    func test_callAsFunction_whenAppleIdentityDoesNotMatchTheAccount_deletesAndRevokesNothing() async {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.reauthenticateError = NSError(domain: AuthErrorDomain, code: AuthErrorCode.userMismatch.rawValue)
        let sut = makeSUT(
            dataProvider: dataProvider,
            log: log,
            linkedProviderKinds: [.apple],
            appleSignInProvider: makeAppleSignInProvider(authorizationCode: "other-identity-code"),
            authCommandProvider: authCommandProvider
        )

        do {
            try await sut()
            XCTFail("Expected ReauthenticateError.wrongAccount to be thrown")
        } catch ReauthenticateError.wrongAccount {
            XCTAssertEqual(log.entries, ["reauthenticate"], "an authorization code of another Apple identity would revoke the wrong grant, so nothing may be revoked or deleted")
            XCTAssertTrue(authCommandProvider.revokedAuthorizationCodes.isEmpty)
        } catch {
            XCTFail("Expected ReauthenticateError.wrongAccount but got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeAppleSignInProvider(authorizationCode: String?) -> AppleSignInProviderFake {
        AppleSignInProviderFake(
            result: AppleSignInResult(
                credential: OAuthProvider.appleCredential(withIDToken: "token", rawNonce: "nonce", fullName: nil),
                fullName: nil,
                email: nil,
                authorizationCode: authorizationCode
            )
        )
    }

    private func makeSUT(
        dataProvider: DeleteAccountDataProviderFake,
        log: OperationLog,
        snapshotStore: PendingMergeSnapshotStoreFake = PendingMergeSnapshotStoreFake(),
        storage: StorageDataProviderFake = StorageDataProviderFake(),
        linkedProviderKinds: Set<AuthProviderKind> = [],
        appleSignInProvider: AppleSignInProviderFake = AppleSignInProviderFake(),
        authCommandProvider: DeleteAccountAuthCommandProviderFake? = nil
    ) -> DeleteAccountUseCase {
        DeleteAccountUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(linkedProviderKinds: linkedProviderKinds),
            authCommandProvider: authCommandProvider ?? DeleteAccountAuthCommandProviderFake(log: log),
            snapshotStore: snapshotStore,
            storageProvider: storage,
            appleSignInProvider: appleSignInProvider
        )
    }

    private func makeSnapshot() -> PendingMergeSnapshot {
        PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [], favouriteFoods: [], myCreatedMeals: [])
    }

    private func makeFood(id: String) -> FoodConsumedDTO {
        FoodConsumedDTO(
            id: id,
            foodItemId: id,
            foodItemKind: .catalogue,
            czName: "Test",
            engName: "Test",
            weight: 100,
            date: Date().timeIntervalSince1970,
            calories: 100,
            caloriesPerHundredGrams: nil,
            energyKJ: nil,
            protein: 1,
            carbohydrate: 1,
            carbohydrateSugar: 1,
            fat: 1,
            fatSaturated: nil,
            fatUnsaturated: 1,
            fiber: 1,
            salt: 1,
            mealTypeId: nil,
            measureUnit: nil,
            alcoholByVolume: nil
        )
    }

    private func makeFavourite(id: String) -> FavouriteFoodDTO {
        FavouriteFoodDTO(
            item: FoodItemDomain(
                id: id,
                kind: .catalogue,
                czName: "Test",
                engName: "Test",
                weight: 100,
                date: .now,
                energyKJ: 400,
                caloriesPerHundredGrams: 100,
                fat: 1,
                fatSaturated: 1,
                fatUnsaturatedFattyAcids: 1,
                carbohydrate: 1,
                carbohydratePureSugar: 1,
                fiber: 1,
                protein: 1,
                salt: 1
            ),
            favouritedAt: .now
        )
    }

    private func makeSubmission(id: String) -> FoodItemSubmissionDTO {
        FoodItemSubmissionDTO(
            id: id,
            barcode: "12345678",
            submittedBy: "test-user-id",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: FoodItemDomain(
                id: "12345678",
                kind: .catalogue,
                czName: "Test",
                engName: "Test",
                weight: 100,
                date: .now,
                energyKJ: 400,
                caloriesPerHundredGrams: 100,
                fat: 1,
                fatSaturated: 1,
                fatUnsaturatedFattyAcids: 1,
                carbohydrate: 1,
                carbohydratePureSugar: 1,
                fiber: 1,
                protein: 1,
                salt: 1
            )
        )
    }

    private func makeMeal(id: String) -> MyCreatedMealDTO {
        MyCreatedMealDTO(
            meal: MyCreatedMealDomain(
                id: id,
                name: "Test",
                ingredients: [
                    MyCreatedMealIngredientDomain(
                        foodItemId: "12345",
                        czName: "Test",
                        engName: "Test",
                        grams: 50,
                        nutrition: FoodNutritionValues(
                            energyKJ: 400,
                            caloriesPerHundredGrams: 100,
                            fat: 1,
                            fatSaturated: 1,
                            fatUnsaturatedFattyAcids: 1,
                            carbohydrate: 1,
                            carbohydratePureSugar: 1,
                            fiber: 1,
                            protein: 1,
                            salt: 1
                        )
                    )
                ],
                createdAt: .now,
                updatedAt: .now
            )
        )
    }
}

private final class OperationLog {
    private(set) var entries: [String] = []
    func record(_ entry: String) { entries.append(entry) }
}

private final class DeleteAccountDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private let log: OperationLog
    var stubbedMealTypes: [MealTypeDTO] = []
    var stubbedFoodConsumed: [FoodConsumedDTO] = []
    var stubbedFavouriteFoods: [FavouriteFoodDTO] = []
    var stubbedMyCreatedMeals: [MyCreatedMealDTO] = []
    var stubbedFoodItemPortions: [FoodItemPersonalPortionsDTO] = []
    var stubbedMergedAnonymousAccounts: [MergedAnonymousAccountDTO] = []
    var stubbedBodyWeights: [BodyWeightDTO] = []
    var stubbedSubmissions: [FoodItemSubmissionDTO] = []
    var stubbedReports: [FoodItemReportDTO] = []
    private(set) var deletedIds: [String] = []

    // MARK: - Init

    init(log: OperationLog) {
        self.log = log
    }

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if collection.hasSuffix("mealTypes") {
            return stubbedMealTypes.compactMap { $0 as? T }
        }
        if collection.hasSuffix("bodyWeights") {
            return stubbedBodyWeights.compactMap { $0 as? T }
        }
        if collection.hasSuffix("favouriteFoods") {
            return stubbedFavouriteFoods.compactMap { $0 as? T }
        }
        if collection.hasSuffix("myCreatedMeals") {
            return stubbedMyCreatedMeals.compactMap { $0 as? T }
        }
        if collection.hasSuffix("foodItemPortions") {
            return stubbedFoodItemPortions.compactMap { $0 as? T }
        }
        if collection.hasSuffix("mergedAnonymousAccounts") {
            return stubbedMergedAnonymousAccounts.compactMap { $0 as? T }
        }
        return stubbedFoodConsumed.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] {
        if collection == Constants.Firestore.foodItemReports {
            return stubbedReports.compactMap { $0 as? T }
        }
        return stubbedSubmissions.compactMap { $0 as? T }
    }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func batchDeleteAsync(ids: [String], from collection: String) async throws {
        log.record("batchDelete:\(collection.split(separator: "/").last ?? ""):\(ids.joined(separator: ","))")
    }

    func deleteAsync(id: String, from collection: String) async throws {
        deletedIds.append(id)
        if collection.hasSuffix("mealTypes") {
            log.record("deleteMealType:\(id)")
        } else if collection.hasSuffix("favouriteFoods") {
            log.record("deleteFavourite:\(id)")
        } else if collection.hasSuffix("myCreatedMeals") {
            log.record("deleteMeal:\(id)")
        } else if collection.hasSuffix("foodItemPortions") {
            log.record("deletePortions:\(id)")
        } else if collection.hasSuffix("mergedAnonymousAccounts") {
            log.record("deleteMergedAnonymousAccount:\(id)")
        } else if collection.hasSuffix("foodConsumed") {
            log.record("deleteFood:\(id)")
        } else if collection.hasSuffix("bodyWeights") {
            log.record("deleteBodyWeight:\(id)")
        } else if collection.hasSuffix("settings") {
            log.record("deleteSettings:\(id)")
        } else if collection.hasSuffix("stats") {
            log.record("deleteStats:\(id)")
        } else if collection == Constants.Firestore.foodItemSubmissions {
            log.record("deleteSubmission:\(id)")
        } else if collection == Constants.Firestore.foodItemReports {
            log.record("deleteReport:\(id)")
        } else {
            log.record("deleteUser:\(id)")
        }
    }
}

private final class DeleteAccountAuthCommandProviderFake: AuthCommandProviderProtocol {

    // MARK: - Properties

    private let log: OperationLog
    var deleteError: Error?
    var revokeError: Error?
    var reauthenticateError: Error?
    private(set) var revokedAuthorizationCodes: [String] = []

    // MARK: - Init

    init(log: OperationLog) {
        self.log = log
    }

    // MARK: - Functions

    func link(with credential: AuthCredential) async throws {}
    func signIn(with credential: AuthCredential) async throws {}

    func reauthenticate(with credential: AuthCredential) async throws {
        log.record("reauthenticate")
        if let reauthenticateError { throw reauthenticateError }
    }

    func signOut() throws {}
    func updateDisplayName(_ name: String) async throws {}

    func deleteCurrentUser() async throws {
        log.record("deleteCurrentUser")
        if let deleteError { throw deleteError }
    }

    func revokeAppleToken(authorizationCode: String) async throws {
        log.record("revokeAppleToken")
        revokedAuthorizationCodes.append(authorizationCode)
        if let revokeError { throw revokeError }
    }
}
