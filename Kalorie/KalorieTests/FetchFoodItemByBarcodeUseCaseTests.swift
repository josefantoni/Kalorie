//
//  FetchFoodItemByBarcodeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 24.07.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemByBarcodeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchByBarcode_withEmptyBarcode_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(barcode: "")
        XCTAssertNil(result)
    }

    func test_fetchByBarcode_whenProviderReturnsNil_returnsNil() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTO = nil
        let result = try await sut(barcode: "8594004428464")
        XCTAssertNil(result)
    }

    func test_fetchByBarcode_whenProviderReturnsDTO_returnsMappedDomain() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTO = makeDTO(id: "8594004428464", czName: "Jihočeský tvaroh")
        let result = try await sut(barcode: "8594004428464")
        XCTAssertEqual(result?.id, "8594004428464")
        XCTAssertEqual(result?.czName, "Jihočeský tvaroh")
        XCTAssertEqual(result?.caloriesPerHundredGrams, 80)
    }

    func test_fetchByBarcode_queriesCorrectField() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTO = makeDTO()
        _ = try await sut(barcode: "1234567890")
        XCTAssertEqual(dataProvider.lastQueriedField, "id")
        XCTAssertEqual(dataProvider.lastQueriedValue, "1234567890")
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchFoodItemByBarcodeUseCase, dataProvider: BarcodeDataProviderFake) {
        let dataProvider = BarcodeDataProviderFake()
        let sut = FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(
        id: String = "8594004428464",
        czName: String = "Tvaroh"
    ) -> FoodItemDTO {
        FoodItemDTO(
            id: id,
            czName: czName,
            engName: "Cottage cheese",
            czNameLowercase: czName.lowercased(),
            engNameLowercase: "cottage cheese",
            weight: 100,
            date: Date.now.timeIntervalSince1970,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class BarcodeDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTO: FoodItemDTO?
    var lastQueriedField: String?
    var lastQueriedValue: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? {
        lastQueriedField = field
        lastQueriedValue = value
        return stubbedDTO as? T
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
