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
            try await sut(makeItem(), grams: 100, date: .now, mealTypes: [])
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_saveFoodConsumed_savesToUserSpecificCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(makeItem(), grams: 100, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedToCollection, "users/user-123/foodConsumed")
    }

    func test_saveFoodConsumed_setsDocumentIdToMatchDTOId() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(), grams: 100, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDocumentId, dataProvider.savedDTO?.id)
    }

    func test_saveFoodConsumed_calculatesCaloriesFromWeightAndCaloriesPer100g() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(caloriesPerHundredGrams: 100), grams: 200, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.calories, 200)
    }

    func test_savesRoundedCalories_whenScalingProducesFraction() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(caloriesPerHundredGrams: 133), grams: 150, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.calories, 200)
    }

    func test_savesRoundedCalories_whenCaloriesPerHundredGramsIsFractional_roundsOnce() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(caloriesPerHundredGrams: 133.6), grams: 150, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.calories, 200)
    }

    func test_saveFoodConsumed_storesTheItemsCaloriesPerHundredGramsUnscaled() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(caloriesPerHundredGrams: 133.6), grams: 150, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.caloriesPerHundredGrams, 133.6)
    }

    func test_saveFoodConsumed_storesCorrectNameAndWeight() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(czName: "Tvaroh", engName: "Cottage cheese"), grams: 150, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.czName, "Tvaroh")
        XCTAssertEqual(dataProvider.savedDTO?.engName, "Cottage cheese")
        XCTAssertEqual(dataProvider.savedDTO?.weight, 150)
    }

    func test_saveFoodConsumed_scalesEnergyKJFatSaturatedAndFiberToLoggedWeight() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(fiber: 3), grams: 200, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.energyKJ, 1296)
        XCTAssertEqual(dataProvider.savedDTO?.fatSaturated, 6)
        XCTAssertEqual(dataProvider.savedDTO?.fiber, 6)
    }

    func test_saveFoodConsumed_whenItemsFiberIsUnknown_staysNilInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(fiber: nil), grams: 200, date: .now, mealTypes: [])
        XCTAssertNil(dataProvider.savedDTO?.fiber)
    }

    func test_saveFoodConsumed_storesTheItemsKindAsFoodItemKind() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeItem(kind: .external), grams: 100, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.savedDTO?.foodItemKind, .external)
    }

    func test_saveFoodConsumed_pinsToTheMealTypeWindowItWasLoggedInto() async throws {
        let (sut, dataProvider) = makeSUT()
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 12, minute: 30, second: 0, of: .now) ?? .now
        try await sut(makeItem(), grams: 100, date: loggedAt, mealTypes: [makeMealType(id: "breakfast", hour: 6, endHour: 10), makeMealType(id: "lunch", hour: 11, endHour: 14)])
        XCTAssertEqual(dataProvider.savedDTO?.mealTypeId, "lunch", "logging must auto-pin to whichever meal window was active at the time, so the user does not have to assign it manually afterwards")
    }

    func test_saveFoodConsumed_whenNoWindowMatches_leavesNoPin() async throws {
        let (sut, dataProvider) = makeSUT()
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 3, minute: 0, second: 0, of: .now) ?? .now
        try await sut(makeItem(), grams: 100, date: loggedAt, mealTypes: [makeMealType(id: "breakfast", hour: 6, endHour: 10)])
        XCTAssertNil(dataProvider.savedDTO?.mealTypeId, "an entry logged outside every meal window has no window to pin to; it falls into the unassigned section, same as before")
    }

    // MARK: - Helpers

    private func makeMealType(id: String, hour: Int, endHour: Int, minute: Int = 0) -> MealTypeDomain {
        let cal = Calendar.current
        let base = Date.now
        let start = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base) ?? base
        let end = cal.date(bySettingHour: endHour, minute: minute, second: 0, of: base) ?? base
        return MealTypeDomain(id: id, name: "Meal \(id)", startTime: start, endTime: end)
    }

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
        caloriesPerHundredGrams: Double = 155,
        fiber: Double? = 0,
        kind: FoodItemKind = .catalogue
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
            kind: kind,
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
            fiber: fiber,
            protein: 13,
            salt: 0.3
        )
    }
}

private final class SaveFoodConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedToCollection: String?
    var savedDTO: FoodConsumedDTO?
    var savedDocumentId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedToCollection = collection
        savedDTO = item as? FoodConsumedDTO
        savedDocumentId = id
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
