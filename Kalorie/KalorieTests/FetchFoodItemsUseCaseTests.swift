//
//  FetchFoodItemsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodItems_whenProviderReturnsEmpty_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchFoodItems_whenProviderReturnsItems_returnsMappedDomains() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [makeDTO(id: "A123", czName: "Tvaroh", caloriesPerHundredGrams: 80)]

        let result = try await sut()

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].id, "A123")
        XCTAssertEqual(result[0].czName, "Tvaroh")
        XCTAssertEqual(result[0].caloriesPerHundredGrams, 80)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchFoodItemsUseCase, dataProvider: FetchFoodItemsDataProviderFake) {
        let dataProvider = FetchFoodItemsDataProviderFake()
        let sut = FetchFoodItemsUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(
        id: String = "12345678",
        czName: String = "Tvaroh",
        caloriesPerHundredGrams: Double = 80
    ) -> FoodItemDTO {
        FoodItemDTO(
            id: id,
            czName: czName,
            engName: "",
            czNameLowercase: czName.lowercased(),
            engNameLowercase: "",
            weight: 100,
            date: Date.now.timeIntervalSince1970,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 0.5,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class FetchFoodItemsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodItemDTO] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        stubbedDTOs.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
