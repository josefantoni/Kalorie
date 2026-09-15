//
//  UpdateFoodItemUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class UpdateFoodItemUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_update_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeItem())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(makeItem(caloriesPerHundredGrams: 0))
            XCTFail("Expected invalidCalories error")
        } catch UpdateFoodItemError.invalidCalories {
            // pass
        }
        XCTAssertFalse(dataProvider.didWrite)
    }

    func test_update_withValidItem_overwritesTheCatalogueDocument() async throws {
        let (sut, dataProvider) = makeSUT()
        let item = makeItem()
        try await sut(item)
        XCTAssertTrue(dataProvider.didWrite)
        XCTAssertEqual(dataProvider.writtenCollection, Constants.Firestore.foodItems)
        XCTAssertEqual(dataProvider.writtenId, item.id)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: UpdateFoodItemUseCase, dataProvider: UpdateFoodItemDataProviderFake) {
        let dataProvider = UpdateFoodItemDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = UpdateFoodItemUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeItem(id: String = "12345678", caloriesPerHundredGrams: Double = 80) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 200,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
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

private final class UpdateFoodItemDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var didWrite = false
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
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        didWrite = true
        writtenCollection = collection
        writtenId = id
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
