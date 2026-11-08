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

    func test_assignFoodMealType_writesOnlyTheMealTypeId_soAPropagatedNameIsNotReverted() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(), mealTypeId: "lunch")
        XCTAssertEqual(
            dataProvider.updatedFields,
            ["meal_type_id": "lunch"],
            "the in-memory name can be older than a propagated rename, so pinning a meal type must not write cz_name/eng_name back"
        )
    }

    func test_assignFoodMealType_updatesInsteadOfOverwritingTheDocument() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(), mealTypeId: "lunch")
        XCTAssertEqual(dataProvider.updatedId, "1")
        XCTAssertFalse(dataProvider.didSet)
    }

    func test_assignFoodMealType_whenTheEntryWasDeletedMeanwhile_throwsNotFoundAndWritesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.storedIds = []
        do {
            try await sut(makeFood(), mealTypeId: "lunch")
            XCTFail("Expected notFound error")
        } catch FirestoreDataProviderError.notFound {
            // pass
        }
        XCTAssertNil(dataProvider.updatedFields)
        XCTAssertFalse(dataProvider.didSet)
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

    var updatedFields: [String: String]?
    var updatedId: String?
    var didSet = false
    var storedIds: Set<String> = ["1"]

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
        didSet = true
    }

    func updateAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        guard storedIds.contains(id) else { throw FirestoreDataProviderError.notFound }
        let data = try JSONEncoder().encode(item)
        updatedFields = try JSONSerialization.jsonObject(with: data) as? [String: String]
        updatedId = id
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
