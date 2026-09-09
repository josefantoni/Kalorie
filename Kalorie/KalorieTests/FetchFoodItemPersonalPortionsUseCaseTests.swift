//
//  FetchFoodItemPersonalPortionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 09.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemPersonalPortionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(barcode: "12345678")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetch_queriesUserSpecificCollectionByBarcode() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        _ = try await sut(barcode: "12345678")
        XCTAssertEqual(dataProvider.queriedCollection, "users/user-123/foodItemPortions")
        XCTAssertEqual(dataProvider.queriedId, "12345678")
    }

    func test_fetch_whenNoDocumentExists_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(barcode: "12345678")
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetch_mapsStubbedDTOToDomain() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTO = FoodItemPersonalPortionsDTO(id: "12345678", portions: [FoodPortionDTO(portion: FoodPortionDomain(name: "2 tyčinky", grams: 66))])
        let result = try await sut(barcode: "12345678")
        XCTAssertEqual(result, [FoodPortionDomain(name: "2 tyčinky", grams: 66)])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchFoodItemPersonalPortionsUseCase, dataProvider: FetchFoodItemPersonalPortionsDataProviderFake) {
        let dataProvider = FetchFoodItemPersonalPortionsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchFoodItemPersonalPortionsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class FetchFoodItemPersonalPortionsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTO: FoodItemPersonalPortionsDTO?
    var queriedCollection: String?
    var queriedId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        queriedCollection = collection
        queriedId = id
        return stubbedDTO as? T
    }

    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
