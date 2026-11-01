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
        dataProvider.stubbedDocumentIds["favouriteFoods"] = ["fav1"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("batchDelete:favouriteFoods:fav1"), "Firestore does not cascade — favourites are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesMyCreatedMealsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["myCreatedMeals"] = ["meal1"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("batchDelete:myCreatedMeals:meal1"), "Firestore does not cascade — created meals are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesFoodItemPortionsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["foodItemPortions"] = ["12345678"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.dropLast().contains("batchDelete:foodItemPortions:12345678"), "Firestore does not cascade — personal portions are user data and must not survive account deletion as orphans")
    }

    func test_callAsFunction_deletesBodyWeightsAndSettingsAsPrivacyObligation_beforeTheProfile() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["bodyWeights"] = ["2026-10-27", "2026-10-26"]
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
        dataProvider.stubbedDocumentIds["mergedAnonymousAccounts"] = ["anon-1"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertFalse(log.entries.contains("deleteMergedAnonymousAccount:anon-1"), "The marker is the cleanup script's only pointer to the abandoned anonymous account")
    }

    func test_callAsFunction_deletesOwnSubmissionsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["foodItemSubmissions"] = ["sub1"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(
            log.entries.dropLast().contains("batchDelete:foodItemSubmissions:sub1"),
            "A pending or rejected submission is the user's own data and must not survive account deletion"
        )
    }

    func test_callAsFunction_deletesOwnReportsAsPrivacyObligation() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["foodItemReports"] = ["12345678_test-user-id"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(
            log.entries.dropLast().contains("batchDelete:foodItemReports:12345678_test-user-id"),
            "A report is the user's own data and must not survive account deletion"
        )
    }

    func test_callAsFunction_deletesFirestoreDataBeforeDeletingAuthAccount() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["mealTypes"] = ["0"]
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
        XCTAssertTrue(log.entries.dropLast().contains("batchDelete:mealTypes:0"))
        XCTAssertTrue(log.entries.dropLast().contains("batchDelete:foodConsumed:f1"))
    }

    func test_callAsFunction_deletesAllMealTypesAndFoodsById() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedDocumentIds["mealTypes"] = ["0", "1"]
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1", "f2"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.contains("batchDelete:mealTypes:0,1"))
        XCTAssertTrue(log.entries.contains("batchDelete:foodConsumed:f1,f2"))
    }

    func test_callAsFunction_whenADocumentDoesNotMatchItsDTO_stillDeletesItAndTheAccount() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.shouldFailDecodingDTOs = true
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["broken"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(log.entries.contains("batchDelete:foodConsumed:broken"), "A document that no longer decodes is still the user's data and must not block the wipe")
        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
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
        dataProvider.stubbedDocumentIds["mealTypes"] = ["0"]
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1"]
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
        dataProvider.stubbedDocumentIds["mealTypes"] = ["0"]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut(skipDataWipe: true)

        XCTAssertEqual(log.entries, ["deleteCurrentUser"], "a retry that already wiped the data must not re-read and re-delete it")
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
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1"]
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
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1"]
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
        dataProvider.stubbedDocumentIds["foodConsumed"] = ["f1"]
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
}

private final class OperationLog {
    private(set) var entries: [String] = []
    func record(_ entry: String) { entries.append(entry) }
}

private final class DeleteAccountDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private let log: OperationLog
    var stubbedDocumentIds: [String: [String]] = [:]
    var shouldFailDecodingDTOs = false

    // MARK: - Init

    init(log: OperationLog) {
        self.log = log
    }

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if shouldFailDecodingDTOs { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "stubbed")) }
        return []
    }

    func loadDocumentIdsAsync(from collection: String) async throws -> [String] {
        stubbedDocumentIds[String(collection.split(separator: "/").last ?? "")] ?? []
    }

    func loadDocumentIdsAsync(from collection: String, where field: String, isEqualTo value: String) async throws -> [String] {
        stubbedDocumentIds[String(collection.split(separator: "/").last ?? "")] ?? []
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] {
        if shouldFailDecodingDTOs { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "stubbed")) }
        return []
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
