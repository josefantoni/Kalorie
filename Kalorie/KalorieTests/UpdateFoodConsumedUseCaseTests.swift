//
//  UpdateFoodConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class UpdateFoodConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_updateFoodConsumed_whenWeightChanges_preservesFoodItemId() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(foodItemId: "12345", weight: 100), newWeight: 200)
        XCTAssertEqual(dataProvider.savedDTO?.foodItemId, "12345")
    }

    func test_updateFoodConsumed_whenWeightChanges_preservesFoodItemKind() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, kind: .createdMeal), newWeight: 200)
        XCTAssertEqual(dataProvider.savedDTO?.foodItemKind, .createdMeal)
    }

    func test_updateFoodConsumed_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeFood(), newWeight: 200)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: UpdateFoodConsumedUseCase, dataProvider: UpdateFoodConsumedDataProviderFake) {
        let dataProvider = UpdateFoodConsumedDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeFood(
        foodItemId: String = "12345",
        weight: Double = 100,
        kind: FoodItemKind = .catalogue
    ) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "1",
            foodItemId: foodItemId,
            foodItemKind: kind,
            czName: "Vejce",
            engName: "Egg",
            weight: weight,
            date: .now,
            calories: 155,
            protein: 13,
            carbohydrate: 1,
            carbohydrateSugar: 0,
            fat: 10,
            fatUnsaturated: 3,
            fiber: 0,
            salt: 0.3
        )
    }
}

private final class UpdateFoodConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedDTO: FoodConsumedDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedDTO = item as? FoodConsumedDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
