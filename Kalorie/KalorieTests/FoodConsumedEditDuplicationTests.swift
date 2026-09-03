//
//  FoodConsumedEditDuplicationTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class FoodConsumedEditDuplicationTests: XCTestCase {

    // MARK: - Tests

    func test_saveThenUpdate_doesNotCreateDuplicateDocument() async throws {
        let dataProvider = FirestoreDocumentStoreFake()
        let authProvider = AuthProviderFake(userId: "user-123")
        let save = SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        let update = UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)

        try await save(makeItem(), grams: 100, date: .now, mealTypes: [])
        XCTAssertEqual(dataProvider.documents.count, 1)

        let savedDTO = try XCTUnwrap(dataProvider.documents.values.first)
        let food = savedDTO.asDomain()

        try await update(food, newWeight: 150)

        XCTAssertEqual(dataProvider.documents.count, 1)
    }

    func test_pinThenUpdateWeight_doesNotUnpinTheEntry() async throws {
        let dataProvider = FirestoreDocumentStoreFake()
        let authProvider = AuthProviderFake(userId: "user-123")
        let save = SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        let assignMealType = AssignFoodMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider)
        let update = UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)

        try await save(makeItem(), grams: 100, date: .now, mealTypes: [])
        let savedFood = try XCTUnwrap(dataProvider.documents.values.first).asDomain()

        try await assignMealType(savedFood, mealTypeId: "breakfast")
        let pinnedFood = try XCTUnwrap(dataProvider.documents[savedFood.id]).asDomain()
        XCTAssertEqual(pinnedFood.mealTypeId, "breakfast")

        try await update(pinnedFood, newWeight: 150)

        XCTAssertEqual(
            dataProvider.documents[savedFood.id]?.mealTypeId,
            "breakfast",
            "setAsync rewrites the whole document, so an editor that does not round-trip meal_type_id would silently undo the user's pin"
        )
    }

    @MainActor
    func test_onSaveAfterPin_throughViewModel_keepsThePin() async throws {
        let dataProvider = FirestoreDocumentStoreFake()
        let authProvider = AuthProviderFake(userId: "user-123")
        let save = SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        try await save(makeItem(), grams: 100, date: .now, mealTypes: [])
        let savedFood = try XCTUnwrap(dataProvider.documents.values.first).asDomain()
        let sut = makeDetailViewModel(food: savedFood, dataProvider: dataProvider, authProvider: authProvider)

        await sut.onMealTypeSelected("breakfast")
        sut.weight = 150
        await sut.onSave()

        XCTAssertEqual(
            dataProvider.documents[savedFood.id]?.mealTypeId,
            "breakfast",
            "onSave must round-trip the pin set earlier in the same screen visit, not the stale snapshot passed at init"
        )
    }

    @MainActor
    func test_onMealTypeSelectedAfterSave_throughViewModel_keepsTheUpdatedWeight() async throws {
        let dataProvider = FirestoreDocumentStoreFake()
        let authProvider = AuthProviderFake(userId: "user-123")
        let save = SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        try await save(makeItem(), grams: 100, date: .now, mealTypes: [])
        let savedFood = try XCTUnwrap(dataProvider.documents.values.first).asDomain()
        let sut = makeDetailViewModel(food: savedFood, dataProvider: dataProvider, authProvider: authProvider)

        sut.weight = 150
        await sut.onSave()
        await sut.onMealTypeSelected("breakfast")

        XCTAssertEqual(
            dataProvider.documents[savedFood.id]?.weight,
            150,
            "onMealTypeSelected must round-trip the weight edit saved earlier in the same screen visit, not the stale snapshot passed at init"
        )
    }

    // MARK: - Helpers

    @MainActor
    private func makeDetailViewModel(
        food: FoodConsumedDomain,
        dataProvider: FirestoreDocumentStoreFake,
        authProvider: AuthProviderFake
    ) -> FoodConsumedDetailViewModel {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: .now, endTime: .now)
        return FoodConsumedDetailViewModel(
            food: food,
            mealTypes: [breakfast],
            updateFoodConsumed: UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
            assignFoodMealType: AssignFoodMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [breakfast]),
            isFavouriteFood: IsFavouriteFoodUseCaseFake(),
            addFavouriteFood: AddFavouriteFoodUseCaseFake(),
            removeFavouriteFood: RemoveFavouriteFoodUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake()
        ) {}
    }

    private func makeItem() -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
            kind: .catalogue,
            czName: "Vejce",
            engName: "Egg",
            weight: 100,
            date: .now,
            energyKJ: 648,
            caloriesPerHundredGrams: 155,
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

private final class FirestoreDocumentStoreFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var documents: [String: FoodConsumedDTO] = [:]

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {
        guard let dto = item as? FoodConsumedDTO else { return }
        documents[UUID().uuidString] = dto
    }

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        guard let dto = item as? FoodConsumedDTO else { return }
        documents[id] = dto
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {
        documents.removeValue(forKey: id)
    }
}
