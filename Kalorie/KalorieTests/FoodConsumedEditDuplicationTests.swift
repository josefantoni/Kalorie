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

        try await save(makeItem(), grams: 100, date: .now)
        XCTAssertEqual(dataProvider.documents.count, 1)

        let savedDTO = try XCTUnwrap(dataProvider.documents.values.first)
        let food = savedDTO.asDomain()

        try await update(food, newWeight: 150)

        XCTAssertEqual(dataProvider.documents.count, 1)
    }

    // MARK: - Helpers

    private func makeItem() -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
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
