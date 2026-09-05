//
//  SearchFoodItemsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 05.09.2026.
//

import XCTest
@testable import Kalorie

final class SearchFoodItemsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_search_mergesResultsFromBothNameFields() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzName = [makeDTO(id: "1", czName: "Tvaroh")]
        dataProvider.stubbedByEngName = [makeDTO(id: "2", czName: "Cottage cheese")]

        let result = try await sut(query: "tv")

        XCTAssertEqual(Set(result.map(\.id)), ["1", "2"])
    }

    func test_search_withSameItemMatchingBothFields_deduplicatesById() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzName = [makeDTO(id: "1", czName: "Tvaroh")]
        dataProvider.stubbedByEngName = [makeDTO(id: "1", czName: "Tvaroh")]

        let result = try await sut(query: "tv")

        XCTAssertEqual(result.count, 1)
    }

    func test_search_whenEnergyKJMissing_computesItFromMacrosInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzName = [try makeDTOMissingEnergyKJ(fat: 10, carbohydrate: 20, protein: 5)]

        let result = try await sut(query: "tv")

        // 10g fat + 20g carbohydrate + 5g protein = 370 + 340 + 85 = 795 kJ — a missing source
        // value must not silently read as 0 kJ for a food that clearly has energy.
        XCTAssertEqual(result.first?.energyKJ, 795)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: SearchFoodItemsUseCase, dataProvider: SearchDataProviderFake) {
        let dataProvider = SearchDataProviderFake()
        let sut = SearchFoodItemsUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(
        id: String = "8594004428464",
        czName: String = "Tvaroh",
        fat: Double = 0.5,
        carbohydrate: Double = 4,
        protein: Double = 13
    ) -> FoodItemDTO {
        FoodItemDTO(
            item: FoodItemDomain(
                id: id,
                kind: .catalogue,
                czName: czName,
                engName: "Cottage cheese",
                weight: 100,
                date: .now,
                energyKJ: 335,
                caloriesPerHundredGrams: 80,
                fat: fat,
                fatSaturated: nil,
                fatUnsaturatedFattyAcids: 0.2,
                carbohydrate: carbohydrate,
                carbohydratePureSugar: 3,
                fiber: nil,
                protein: protein,
                salt: 0.1
            )
        )
    }

    private func makeDTOMissingEnergyKJ(fat: Double, carbohydrate: Double, protein: Double) throws -> FoodItemDTO {
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(makeDTO(fat: fat, carbohydrate: carbohydrate, protein: protein))) as? [String: Any] ?? [:]
        json.removeValue(forKey: "energy_kj")
        return try JSONDecoder().decode(FoodItemDTO.self, from: JSONSerialization.data(withJSONObject: json))
    }
}

private final class SearchDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedByCzName: [FoodItemDTO] = []
    var stubbedByEngName: [FoodItemDTO] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] {
        switch field {
        case "cz_name_lowercase": return stubbedByCzName as? [T] ?? []
        case "eng_name_lowercase": return stubbedByEngName as? [T] ?? []
        default: return []
        }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
