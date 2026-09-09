//
//  SaveFoodItemPersonalPortionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 09.09.2026.
//

import XCTest
@testable import Kalorie

final class SaveFoodItemPersonalPortionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_save_whenNotAuthenticated_throwsAuthErrorAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut(barcode: "12345678", portions: [makePortion()])
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_save_withEmptyName_throwsInvalidNameAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(barcode: "12345678", portions: [makePortion(name: "")])
            XCTFail("Expected invalidName error")
        } catch FoodPortionError.invalidName {}
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_save_withGramsBelowOne_throwsInvalidGramsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(barcode: "12345678", portions: [makePortion(grams: 0)])
            XCTFail("Expected invalidGrams error")
        } catch FoodPortionError.invalidGrams {}
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_save_withTooManyPortions_throwsTooManyAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let portions = (0..<(FoodPortionValidation.maxPortions + 1)).map { makePortion(name: "Porce \($0)") }
        do {
            try await sut(barcode: "12345678", portions: portions)
            XCTFail("Expected tooMany error")
        } catch FoodPortionError.tooMany {}
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_save_withValidPortions_writesWholeListToUserSpecificDocument() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(barcode: "12345678", portions: [makePortion(name: "1 balení", grams: 33)])
        XCTAssertEqual(dataProvider.savedToCollection, "users/user-123/foodItemPortions")
        XCTAssertEqual(dataProvider.savedDocumentId, "12345678")
        XCTAssertEqual(dataProvider.savedDTO?.id, "12345678")
        XCTAssertEqual(dataProvider.savedDTO?.portions.map(\.name), ["1 balení"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: SaveFoodItemPersonalPortionsUseCase, dataProvider: SaveFoodItemPersonalPortionsDataProviderFake) {
        let dataProvider = SaveFoodItemPersonalPortionsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = SaveFoodItemPersonalPortionsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makePortion(name: String = "1 balení", grams: Double = 33) -> FoodPortionDomain {
        FoodPortionDomain(name: name, grams: grams)
    }
}

private final class SaveFoodItemPersonalPortionsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedToCollection: String?
    var savedDocumentId: String?
    var savedDTO: FoodItemPersonalPortionsDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedToCollection = collection
        savedDocumentId = id
        savedDTO = item as? FoodItemPersonalPortionsDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
