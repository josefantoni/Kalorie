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

    func test_search_mergesResultsFromBothFoldedNameFields() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1", czName: "Tvaroh")]
        dataProvider.stubbedByEngNameFolded = [makeDTO(id: "2", czName: "Cottage cheese")]

        let result = try await sut(query: "tv")

        XCTAssertEqual(Set(result.map(\.id)), ["1", "2"])
    }

    func test_search_withSameItemMatchingBothFoldedFields_deduplicatesById() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1", czName: "Tvaroh")]
        dataProvider.stubbedByEngNameFolded = [makeDTO(id: "1", czName: "Tvaroh")]

        let result = try await sut(query: "tv")

        XCTAssertEqual(result.count, 1)
    }

    func test_search_mergesInFoldedCzFoldedEnTokenCzTokenEnOrder() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByEngNameToken = [makeDTO(id: "4")]
        dataProvider.stubbedByCzNameToken = [makeDTO(id: "3")]
        dataProvider.stubbedByEngNameFolded = [makeDTO(id: "2")]
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1")]

        let result = try await sut(query: "tv")

        XCTAssertEqual(result.map(\.id), ["1", "2", "3", "4"])
    }

    func test_search_withSingleCharacter_doesNotQueryFirestore() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1")]

        let single = try await sut(query: "m")
        let padded = try await sut(query: " m ")

        XCTAssertEqual(single, [])
        XCTAssertEqual(padded, [])
        XCTAssertEqual(dataProvider.queriedFields, [])
    }

    func test_search_withTwoCharacters_queriesFirestore() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1")]

        let result = try await sut(query: "ml")

        XCTAssertEqual(result.map(\.id), ["1"])
        XCTAssertEqual(
            Set(dataProvider.queriedFields),
            ["cz_name_folded", "eng_name_folded", "cz_name_search_terms", "eng_name_search_terms"]
        )
    }

    func test_search_whenEnergyKJMissing_computesItFromMacrosInsteadOfZero() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [try makeDTOMissingEnergyKJ(fat: 10, carbohydrate: 20, protein: 5)]

        let result = try await sut(query: "tv")

        // 10g fat + 20g carbohydrate + 5g protein = 370 + 340 + 85 = 795 kJ — a missing source
        // value must not silently read as 0 kJ for a food that clearly has energy.
        XCTAssertEqual(result.first?.energyKJ, 795)
    }

    func test_search_withQueryMissingDiacritics_matchesFoldedNameField() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1", czName: "Rohlík")]

        let result = try await sut(query: "rohlik")

        XCTAssertEqual(result.map(\.id), ["1"])
    }

    func test_search_matchesASecondWordByItsToken() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameToken = [makeDTO(id: "1", czName: "Polotučné mléko")]

        let result = try await sut(query: "mlék")

        XCTAssertEqual(result.map(\.id), ["1"])
        XCTAssertEqual(dataProvider.arrayContainsValuesByField["cz_name_search_terms"], "mlek")
        XCTAssertEqual(dataProvider.arrayContainsValuesByField["eng_name_search_terms"], "mlek")
    }

    func test_search_withMultiWordQuery_matchesTokenFieldByLastWordOnly() async throws {
        let (sut, dataProvider) = makeSUT()

        _ = try await sut(query: "polotučné mlék")

        XCTAssertEqual(dataProvider.arrayContainsValuesByField["cz_name_search_terms"], "mlek")
    }

    func test_search_withResultFromBothPrefixAndTokenFields_deduplicatesById() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByCzNameFolded = [makeDTO(id: "1", czName: "Mléko")]
        dataProvider.stubbedByCzNameToken = [makeDTO(id: "1", czName: "Mléko")]

        let result = try await sut(query: "mlék")

        XCTAssertEqual(result.count, 1)
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

    var stubbedByCzNameFolded: [FoodItemDTO] = []
    var stubbedByEngNameFolded: [FoodItemDTO] = []
    var stubbedByCzNameToken: [FoodItemDTO] = []
    var stubbedByEngNameToken: [FoodItemDTO] = []
    private let arrayContainsValuesLock = NSLock()
    private var _arrayContainsValuesByField: [String: String] = [:]
    private var _queriedFields: [String] = []
    var arrayContainsValuesByField: [String: String] {
        arrayContainsValuesLock.withLock { _arrayContainsValuesByField }
    }
    var queriedFields: [String] {
        arrayContainsValuesLock.withLock { _queriedFields }
    }

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] {
        arrayContainsValuesLock.withLock { _queriedFields.append(field) }
        switch field {
        case "cz_name_lowercase", "eng_name_lowercase":
            XCTFail("The search must not query \(field)")
            return []
        case "cz_name_folded": return stubbedByCzNameFolded as? [T] ?? []
        case "eng_name_folded": return stubbedByEngNameFolded as? [T] ?? []
        default: return []
        }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] {
        arrayContainsValuesLock.withLock {
            _arrayContainsValuesByField[field] = value
            _queriedFields.append(field)
        }
        switch field {
        case "cz_name_search_terms": return stubbedByCzNameToken as? [T] ?? []
        case "eng_name_search_terms": return stubbedByEngNameToken as? [T] ?? []
        default: return []
        }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
