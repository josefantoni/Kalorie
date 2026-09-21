//
//  UpdateMySubmissionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class UpdateMySubmissionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_update_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(id: "sub-1", item: makeItem())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            _ = try await sut(id: "sub-1", item: makeItem(name: ""))
            XCTFail("Expected invalidName error")
        } catch FoodItemSubmissionError.invalidName {
            // pass
        }
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_update_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let item = makeItem()
        dataProvider.stubbedExistingFoodItem = FoodItemDTO(item: item)
        do {
            _ = try await sut(id: "sub-1", item: item)
            XCTFail("Expected itemAlreadyExists error")
        } catch FoodItemSubmissionError.itemAlreadyExists {
            // pass
        }
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_update_withValidItem_resetsStatusToPendingUnderTheSameSubmissionId() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        let item = makeItem()
        let result = try await sut(id: "sub-1", item: item)
        XCTAssertEqual(result.id, "sub-1")
        XCTAssertEqual(result.barcode, item.id)
        XCTAssertEqual(result.submittedBy, "user-123")
        XCTAssertEqual(result.status, .pending)
        XCTAssertNil(result.rejectReason)
        XCTAssertEqual(dataProvider.writtenCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.writtenId, "sub-1")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: UpdateMySubmissionUseCase, dataProvider: UpdateMySubmissionDataProviderFake) {
        let dataProvider = UpdateMySubmissionDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = UpdateMySubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeItem(id: String = "12345678", name: String = "Tvaroh") -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: name,
            engName: "Cottage cheese",
            weight: 200,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class UpdateMySubmissionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedExistingFoodItem: FoodItemDTO?
    var didWriteSubmission = false
    var writtenCollection: String?
    var writtenId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { stubbedExistingFoodItem as? T }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        didWriteSubmission = true
        writtenCollection = collection
        writtenId = id
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
