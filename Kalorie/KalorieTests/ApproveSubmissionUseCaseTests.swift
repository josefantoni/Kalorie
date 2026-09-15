//
//  ApproveSubmissionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class ApproveSubmissionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_approve_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _, _) = makeSUT(userId: nil)
        let submission = makeSubmission()
        do {
            try await sut(id: submission.id, item: submission.item)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_approve_withValidSubmission_createsCatalogueItemThenDeletesSubmission() async throws {
        let (sut, dataProvider, createFoodItem) = makeSUT()
        let submission = makeSubmission()
        try await sut(id: submission.id, item: submission.item)
        XCTAssertEqual(createFoodItem.receivedItem?.id, submission.item.id)
        XCTAssertEqual(dataProvider.deletedId, submission.id)
        XCTAssertEqual(dataProvider.deletedCollection, Constants.Firestore.foodItemSubmissions)
    }

    func test_approve_whenBarcodeEnteredCatalogueAfterSubmissionWasFiled_refusesAndKeepsSubmission() async throws {
        let (sut, dataProvider, createFoodItem) = makeSUT()
        createFoodItem.errorToThrow = CreateFoodItemError.itemAlreadyExists
        let submission = makeSubmission()
        do {
            try await sut(id: submission.id, item: submission.item)
            XCTFail("Expected itemAlreadyExists error")
        } catch CreateFoodItemError.itemAlreadyExists {
            // pass
        }
        XCTAssertNil(dataProvider.deletedId, "A submission that collides with an approved barcode must survive for the maintainer to resolve by hand")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: ApproveSubmissionUseCase, dataProvider: ApproveSubmissionDataProviderFake, createFoodItem: CreateFoodItemUseCaseStub) {
        let dataProvider = ApproveSubmissionDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let createFoodItem = CreateFoodItemUseCaseStub()
        let sut = ApproveSubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider, createFoodItem: createFoodItem)
        return (sut, dataProvider, createFoodItem)
    }

    private func makeSubmission(id: String = "sub-1", barcode: String = "12345678") -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: barcode,
            submittedBy: "some-user",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: FoodItemDomain(
                id: barcode,
                kind: .catalogue,
                czName: "Tvaroh",
                engName: "",
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
        )
    }
}

private final class CreateFoodItemUseCaseStub: CreateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?
    private(set) var receivedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        receivedItem = item
        if let errorToThrow { throw errorToThrow }
        return item
    }
}

private final class ApproveSubmissionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedId: String?
    var deletedCollection: String?

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
        deletedId = id
        deletedCollection = collection
    }
}
