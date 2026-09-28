//
//  CopyFoodsConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 28.09.2026.
//

import XCTest
@testable import Kalorie

final class CopyFoodsConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_copy_whenNotAuthenticated_throwsAuthErrorAndWritesNothing() async {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut([makeFood(id: "a")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {} catch {
            XCTFail("Unexpected error \(error)")
        }
        XCTAssertNil(dataProvider.batchCollection)
    }

    func test_copy_writesEveryEntryInOneBatchIntoTheUserFoodConsumedCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut([makeFood(id: "a"), makeFood(id: "b")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        XCTAssertEqual(dataProvider.batchCollection, "users/user-123/foodConsumed")
        XCTAssertEqual(dataProvider.batchCallCount, 1, "a copy must be atomic: one batch, so either every entry lands or none")
        XCTAssertEqual(dataProvider.writtenDTOs.count, 2)
    }

    func test_copy_givesEveryEntryANewIdMatchingItsDocumentId() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut([makeFood(id: "a"), makeFood(id: "b")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        let ids = dataProvider.writtenDTOs.map(\.id)
        XCTAssertEqual(Set(ids).count, 2)
        XCTAssertFalse(ids.contains("a") || ids.contains("b"), "reusing the source id would overwrite the original")
        XCTAssertEqual(ids, dataProvider.writtenDocumentIds)
    }

    func test_copy_alwaysPinsTheTargetMealTypeEvenWhenTheSourceWasPinnedElsewhere() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut([makeFood(id: "a", mealTypeId: "breakfast")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        XCTAssertEqual(dataProvider.writtenDTOs.first?.mealTypeId, "lunch")
    }

    func test_copy_carriesMeasureUnitKindAndNutritionUnchanged() async throws {
        let (sut, dataProvider) = makeSUT()
        let source = makeFood(id: "a", kind: .createdMeal, measure: .millilitres)
        try await sut([source], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        let copy = try XCTUnwrap(dataProvider.writtenDTOs.first)
        XCTAssertEqual(copy.measureUnit, FoodMeasure.millilitres.rawValue, "dropping the unit would relabel a millilitre entry as grams")
        XCTAssertEqual(copy.foodItemKind, .createdMeal, "dropping the kind makes the whole day fail to fetch")
        XCTAssertEqual(copy.foodItemId, source.foodItemId)
        XCTAssertEqual(copy.weight, source.weight)
        XCTAssertEqual(copy.calories, source.calories)
        XCTAssertEqual(copy.protein, source.protein)
        XCTAssertEqual(copy.caloriesPerHundredGrams, source.caloriesPerHundredGrams)
    }

    func test_copy_toAnotherDay_usesTheTargetWindowStartOnThatDay() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut([makeFood(id: "a")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        let copy = try XCTUnwrap(dataProvider.writtenDTOs.first)
        let expected = try XCTUnwrap(Calendar.current.date(bySettingHour: 11, minute: 0, second: 0, of: pastDay))
        XCTAssertEqual(copy.date, expected.timeIntervalSince1970)
    }

    func test_copy_keepsSourceOrderByOffsettingEachEntryOneSecond() async throws {
        let (sut, dataProvider) = makeSUT()
        let foods = [makeFood(id: "a"), makeFood(id: "b"), makeFood(id: "c")]
        try await sut(foods, toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        let dates = dataProvider.writtenDTOs.map(\.date)
        XCTAssertEqual(dates[1] - dates[0], 1)
        XCTAssertEqual(dates[2] - dates[1], 1)
    }

    func test_copy_toTodayWhileNowIsInsideTheTargetWindow_usesNow() async throws {
        let now = try XCTUnwrap(Calendar.current.date(bySettingHour: 12, minute: 30, second: 40, of: Date.now))
        let (sut, dataProvider) = makeSUT(now: now)
        try await sut([makeFood(id: "a")], toDay: now, mealTypeId: "lunch", mealTypes: mealTypes)
        let copy = try XCTUnwrap(dataProvider.writtenDTOs.first)
        let expected = try XCTUnwrap(Calendar.current.date(bySettingHour: 12, minute: 30, second: 0, of: now))
        XCTAssertEqual(copy.date, expected.timeIntervalSince1970)
    }

    func test_copy_toTodayWhileNowIsOutsideTheTargetWindow_usesWindowStart() async throws {
        let now = try XCTUnwrap(Calendar.current.date(bySettingHour: 8, minute: 15, second: 0, of: Date.now))
        let (sut, dataProvider) = makeSUT(now: now)
        try await sut([makeFood(id: "a")], toDay: now, mealTypeId: "lunch", mealTypes: mealTypes)
        let copy = try XCTUnwrap(dataProvider.writtenDTOs.first)
        let expected = try XCTUnwrap(Calendar.current.date(bySettingHour: 11, minute: 0, second: 0, of: now))
        XCTAssertEqual(copy.date, expected.timeIntervalSince1970)
    }

    func test_copy_toAPastDayWhileNowWouldFitTheWindow_stillUsesWindowStart() async throws {
        let now = try XCTUnwrap(Calendar.current.date(bySettingHour: 12, minute: 30, second: 0, of: Date.now))
        let (sut, dataProvider) = makeSUT(now: now)
        try await sut([makeFood(id: "a")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
        let copy = try XCTUnwrap(dataProvider.writtenDTOs.first)
        let expected = try XCTUnwrap(Calendar.current.date(bySettingHour: 11, minute: 0, second: 0, of: pastDay))
        XCTAssertEqual(copy.date, expected.timeIntervalSince1970)
    }

    func test_copy_whenTargetMealTypeNoLongerExists_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut([makeFood(id: "a")], toDay: pastDay, mealTypeId: "deleted", mealTypes: mealTypes)
            XCTFail("Expected mealTypeNotFound error")
        } catch CopyFoodsConsumedError.mealTypeNotFound {} catch {
            XCTFail("Unexpected error \(error)")
        }
        XCTAssertNil(dataProvider.batchCollection)
    }

    func test_copy_whenBatchFails_propagatesTheError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.batchError = URLError(.notConnectedToInternet)
        do {
            try await sut([makeFood(id: "a")], toDay: pastDay, mealTypeId: "lunch", mealTypes: mealTypes)
            XCTFail("Expected the batch error")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        } catch {
            XCTFail("Unexpected error \(error)")
        }
    }

    // MARK: - Helpers

    private var pastDay: Date {
        Calendar.current.date(byAdding: .day, value: -3, to: Date.now) ?? Date.now
    }

    private var mealTypes: [MealTypeDomain] {
        [
            MealTypeDomain(id: "breakfast", name: "Breakfast", startMinutes: 7 * 60, endMinutes: 10 * 60),
            MealTypeDomain(id: "lunch", name: "Lunch", startMinutes: 11 * 60, endMinutes: 14 * 60)
        ]
    }

    private func makeSUT(
        userId: String? = "test-user",
        now: Date = .now
    ) -> (sut: CopyFoodsConsumedUseCase, dataProvider: CopyFoodsConsumedDataProviderFake) {
        let dataProvider = CopyFoodsConsumedDataProviderFake()
        let sut = CopyFoodsConsumedUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId)
        ) { now }
        return (sut, dataProvider)
    }

    private func makeFood(
        id: String,
        kind: FoodItemKind = .catalogue,
        measure: FoodMeasure = .grams,
        mealTypeId: String? = nil
    ) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: id,
            foodItemId: "item-\(id)",
            foodItemKind: kind,
            czName: "Jídlo",
            engName: "Food",
            weight: 150,
            date: Date.now,
            calories: 300,
            caloriesPerHundredGrams: 200,
            energyKJ: 1255,
            protein: 12,
            carbohydrate: 30,
            carbohydrateSugar: 6,
            fat: 8,
            fatSaturated: 2,
            fatUnsaturated: 4,
            fiber: 3,
            salt: 0.4,
            mealTypeId: mealTypeId,
            measure: measure
        )
    }
}

final class CopyFoodsConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var batchError: Error?
    private(set) var batchCollection: String?
    private(set) var batchCallCount = 0
    private(set) var writtenDTOs: [FoodConsumedDTO] = []
    private(set) var writtenDocumentIds: [String] = []

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
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        if let batchError { throw batchError }
        batchCallCount += 1
        batchCollection = collection
        writtenDTOs = items.compactMap { $0.item as? FoodConsumedDTO }
        writtenDocumentIds = items.map(\.id)
    }
    func deleteAsync(id: String, from collection: String) async throws {}
}
