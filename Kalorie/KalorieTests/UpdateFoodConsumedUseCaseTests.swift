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

    func test_updateFoodConsumed_recalculatesCaloriesFromStoredPerHundredGramBasis_avoidingCompoundedRounding() async throws {
        let (sut, dataProvider) = makeSUT()
        // 1g of a 33 kcal/100g food logs to 0 kcal (rounds down). Rescaling that rounded 0 by
        // newWeight/oldWeight would stay 0 forever; rescaling from the stored per-100g basis does not.
        try await sut(makeFood(weight: 1, calories: 0, caloriesPerHundredGrams: 33), newWeight: 100)
        XCTAssertEqual(dataProvider.savedDTO?.calories, 33)
    }

    func test_updateFoodConsumed_preservesCaloriesPerHundredGrams() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, caloriesPerHundredGrams: 155), newWeight: 200)
        XCTAssertEqual(dataProvider.savedDTO?.caloriesPerHundredGrams, 155)
    }

    func test_updateFoodConsumed_whenWeightChanges_scalesEnergyKJAndFatSaturated() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, energyKJ: 200, fatSaturated: 4), newWeight: 200)
        XCTAssertEqual(dataProvider.savedDTO?.energyKJ, 400)
        XCTAssertEqual(dataProvider.savedDTO?.fatSaturated, 8)
    }

    func test_updateFoodConsumed_whenExistingWeightIsNotPositive_throwsInvalidWeightError() async throws {
        let (sut, _) = makeSUT()
        do {
            try await sut(makeFood(weight: 0), newWeight: 200)
            XCTFail("Expected invalidWeight error")
        } catch UpdateFoodConsumedError.invalidWeight {
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
        calories: Int = 155,
        caloriesPerHundredGrams: Double = 155,
        energyKJ: Double = 649,
        fatSaturated: Double? = 3,
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
            calories: calories,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            energyKJ: energyKJ,
            protein: 13,
            carbohydrate: 1,
            carbohydrateSugar: 0,
            fat: 10,
            fatSaturated: fatSaturated,
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
