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

    func test_updateFoodConsumed_writesOnlyWeightAndScaledMacros_soAPropagatedNameIsNotReverted() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100), newWeight: 200)
        let keys = try dataProvider.mergedKeys()
        XCTAssertEqual(
            keys,
            [
                "weight", "calories", "calories_per_hundred_grams", "energy_kj", "protein", "carbohydrate",
                "carbohydrate_sugar", "fat", "fat_saturated", "fat_unsaturated", "fiber", "salt"
            ],
            "the in-memory name can be older than a propagated rename, so an edit must not write cz_name/eng_name back"
        )
    }

    func test_updateFoodConsumed_updatesInsteadOfOverwritingTheDocument() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100), newWeight: 200)
        XCTAssertEqual(dataProvider.mergedId, "1")
        XCTAssertFalse(dataProvider.didSet)
    }

    func test_updateFoodConsumed_whenTheEntryWasDeletedMeanwhile_throwsNotFoundAndWritesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.storedIds = []
        do {
            try await sut(makeFood(), newWeight: 200)
            XCTFail("Expected notFound error")
        } catch FirestoreDataProviderError.notFound {
            // pass
        }
        XCTAssertNil(dataProvider.mergedDTO, "a weight edit of a deleted entry must not recreate a document with only the weight and macros")
        XCTAssertFalse(dataProvider.didSet)
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
        XCTAssertEqual(dataProvider.mergedDTO?.calories, 33)
    }

    func test_updateFoodConsumed_preservesCaloriesPerHundredGrams() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, caloriesPerHundredGrams: 155), newWeight: 200)
        XCTAssertEqual(dataProvider.mergedDTO?.caloriesPerHundredGrams, 155)
    }

    func test_updateFoodConsumed_whenWeightChanges_scalesEnergyKJFatSaturatedAndFiber() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, energyKJ: 200, fatSaturated: 4, fiber: 2), newWeight: 200)
        XCTAssertEqual(dataProvider.mergedDTO?.energyKJ, 400)
        XCTAssertEqual(dataProvider.mergedDTO?.fatSaturated, 8)
        XCTAssertEqual(dataProvider.mergedDTO?.fiber, 4)
    }

    func test_updateFoodConsumed_whenFoodsSaturatedFatIsUnknown_staysNilInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, fatSaturated: nil), newWeight: 200)
        XCTAssertNil(dataProvider.mergedDTO?.fatSaturated)
    }

    func test_updateFoodConsumed_whenFoodsFiberIsUnknown_staysNilInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeFood(weight: 100, fiber: nil), newWeight: 200)
        XCTAssertNil(dataProvider.mergedDTO?.fiber)
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
        fiber: Double? = 0,
        kind: FoodItemKind = .catalogue,
        mealTypeId: String? = nil,
        measure: FoodMeasure = .grams
    ) -> FoodConsumedDomain {
        var food = FoodConsumedDomain(
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
            fiber: fiber,
            salt: 0.3,
            mealTypeId: mealTypeId
        )
        food.measure = measure
        return food
    }
}

private final class UpdateFoodConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var mergedDTO: FoodConsumedWeightUpdateDTO?
    var mergedId: String?
    var didSet = false
    var storedIds: Set<String> = ["1"]

    func mergedKeys() throws -> Set<String> {
        let data = try JSONEncoder().encode(XCTUnwrap(mergedDTO))
        return Set(try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any]).keys)
    }

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
        mergedDTO = item as? FoodConsumedWeightUpdateDTO
        mergedId = id
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
