//
//  SaveFoodConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 01.07.2026.
//

import XCTest
@testable import Kalorie

final class SaveFoodConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_saveFoodConsumed_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeItem(), date: .now)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_saveFoodConsumed_savesToUserSpecificCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(makeItem(), date: .now)
        XCTAssertEqual(dataProvider.savedToCollection, "users/user-123/foodConsumed")
    }

    func test_saveFoodConsumed_calculatesCaloriesFromWeightAndCaloriesPer100g() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(weight: 200, caloriesPerHundredGrams: 100), date: .now)
        XCTAssertEqual(dataProvider.savedDTO?.calories, 200)
    }

    func test_saveFoodConsumed_storesCorrectNameAndWeight() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(czName: "Tvaroh", engName: "Cottage cheese", weight: 150), date: .now)
        XCTAssertEqual(dataProvider.savedDTO?.czName, "Tvaroh")
        XCTAssertEqual(dataProvider.savedDTO?.engName, "Cottage cheese")
        XCTAssertEqual(dataProvider.savedDTO?.weight, 150)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: SaveFoodConsumedUseCase, dataProvider: SaveFoodConsumedDataProviderFake) {
        let dataProvider = SaveFoodConsumedDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeItem(
        czName: String = "Vejce",
        engName: String = "Egg",
        weight: Double = 100,
        caloriesPerHundredGrams: Double = 155
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
            czName: czName,
            engName: engName,
            weight: weight,
            date: .now,
            energyKJ: 648,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 10,
            fatSaturated: 3,
            fatUnsaturatedFattyAcids: 3,
            carbohydrate: 1,
            carbohydratePureSugar: 0,
            fiber: 0,
            protein: 13,
            salt: 0.3
        )
    }
}

private final class SaveFoodConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedToCollection: String?
    var savedDTO: FoodConsumedDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {
        savedToCollection = collection
        savedDTO = item as? FoodConsumedDTO
    }

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
