//
//  DeleteMySubmissionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.09.2026.
//

import XCTest
@testable import Kalorie

final class DeleteMySubmissionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteMySubmission_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, dataProvider, _) = makeSUT(userId: nil)
        do {
            try await sut(id: "sub-1", photoURL: nil)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertNil(dataProvider.deletedId)
    }

    func test_deleteMySubmission_deletesFromFoodItemSubmissionsCollection() async throws {
        let (sut, dataProvider, _) = makeSUT(userId: "user-123")
        try await sut(id: "sub-1", photoURL: nil)
        XCTAssertEqual(dataProvider.deletedFromCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.deletedId, "sub-1")
    }

    func test_deleteMySubmission_alsoDeletesItsPhoto() async throws {
        let (sut, _, storage) = makeSUT(userId: "user-123")
        let photo = try await storage.uploadAsync(data: Data([1]), path: "submissionPhotos/user-123/a.jpg", contentType: "image/jpeg")
        try await sut(id: "sub-1", photoURL: photo)
        XCTAssertEqual(storage.deletedURLs, [photo])
    }

    func test_deleteMySubmission_whenThePhotoCannotBeDeleted_stillSucceeds() async throws {
        let (sut, dataProvider, storage) = makeSUT(userId: "user-123")
        storage.deleteError = URLError(.notConnectedToInternet)
        let photo = try XCTUnwrap(URL(string: "https://storage.fake/a.jpg"))
        try await sut(id: "sub-1", photoURL: photo)
        XCTAssertEqual(dataProvider.deletedId, "sub-1", "the submission is withdrawn; an orphan file is only logged and removed with the account")
    }

    func test_deleteMySubmission_whenTheDocumentCannotBeDeleted_keepsThePhoto() async throws {
        let (sut, dataProvider, storage) = makeSUT(userId: "user-123")
        dataProvider.deleteError = URLError(.notConnectedToInternet)
        let photo = try await storage.uploadAsync(data: Data([1]), path: "submissionPhotos/user-123/a.jpg", contentType: "image/jpeg")
        _ = try? await sut(id: "sub-1", photoURL: photo)
        XCTAssertTrue(storage.deletedURLs.isEmpty, "a submission that is still there must keep its photo")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: DeleteMySubmissionUseCase, dataProvider: DeleteMySubmissionDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = DeleteMySubmissionDataProviderFake()
        let storage = StorageDataProviderFake()
        let sut = DeleteMySubmissionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId),
            deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storage)
        )
        return (sut, dataProvider, storage)
    }
}

private final class DeleteMySubmissionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedFromCollection: String?
    var deletedId: String?
    var deleteError: Error?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func deleteAsync(id: String, from collection: String) async throws {
        if let deleteError { throw deleteError }
        deletedId = id
        deletedFromCollection = collection
    }
}
