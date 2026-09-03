//
//  AssignFoodMealTypeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 04.09.2026.
//

import XCTest
@testable import Kalorie

final class AssignFoodMealTypeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_assignFoodMealType_setsMealTypeId() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(), mealTypeId: "lunch")
        XCTAssertEqual(dataProvider.savedDTO?.mealTypeId, "lunch")
    }

    func test_assignFoodMealType_preservesFoodItemId() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(foodItemId: "12345"), mealTypeId: "lunch")
        XCTAssertEqual(
            dataProvider.savedDTO?.foodItemId,
            "12345",
            "setAsync overwrites the whole document, so a writer that drops a field would silently corrupt it while only meaning to move the meal-type pin"
        )
    }

    func test_assignFoodMealType_preservesWeightAndCalories() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 150, calories: 200), mealTypeId: "lunch")
        XCTAssertEqual(dataProvider.savedDTO?.weight, 150)
        XCTAssertEqual(dataProvider.savedDTO?.calories, 200)
    }

    func test_assignFoodMealType_whenFoodsFiberIsUnknown_staysNilInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(fiber: nil), mealTypeId: "lunch")
        XCTAssertNil(dataProvider.savedDTO?.fiber)
    }

    func test_assignFoodMealType_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeFood(), mealTypeId: "lunch")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: AssignFoodMealTypeUseCase, dataProvider: AssignFoodMealTypeDataProviderFake) {
        let dataProvider = AssignFoodMealTypeDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = AssignFoodMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeFood(
        foodItemId: String = "12345",
        weight: Double = 100,
        calories: Int = 155,
        fiber: Double? = 0
    ) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "1",
            foodItemId: foodItemId,
            foodItemKind: .catalogue,
            czName: "Vejce",
            engName: "Egg",
            weight: weight,
            date: .now,
            calories: calories,
            caloriesPerHundredGrams: 155,
            energyKJ: 649,
            protein: 13,
            carbohydrate: 1,
            carbohydrateSugar: 0,
            fat: 10,
            fatSaturated: 3,
            fatUnsaturated: 3,
            fiber: fiber,
            salt: 0.3,
            mealTypeId: nil
        )
    }
}

private final class AssignFoodMealTypeDataProviderFake: FirestoreDataProviderProtocol {

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
