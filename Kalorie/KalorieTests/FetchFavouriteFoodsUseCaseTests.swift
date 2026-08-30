//
//  FetchFavouriteFoodsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class FetchFavouriteFoodsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFavouriteFoods_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_fetchFavouriteFoods_queriesUserSpecificCollectionOrderedByFavouritedAtDescending() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, "users/user-123/favouriteFoods")
        XCTAssertEqual(dataProvider.queriedOrderByField, "favourited_at")
        XCTAssertEqual(dataProvider.queriedDescending, true)
        XCTAssertEqual(dataProvider.queriedLimit, 50)
    }

    func test_fetchFavouriteFoods_mapsStubbedDTOsToDomains() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [makeDTO(id: "12345", czName: "Tvaroh")]
        let result = try await sut()
        XCTAssertEqual(result.map(\.id), ["12345"])
        XCTAssertEqual(result.map(\.czName), ["Tvaroh"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchFavouriteFoodsUseCase, dataProvider: FetchFavouriteFoodsDataProviderFake) {
        let dataProvider = FetchFavouriteFoodsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchFavouriteFoodsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(id: String, czName: String) -> FavouriteFoodDTO {
        FavouriteFoodDTO(
            item: FoodItemDomain(
                id: id,
                kind: .catalogue,
                czName: czName,
                engName: "",
                weight: 100,
                date: .now,
                energyKJ: 0,
                caloriesPerHundredGrams: 100,
                fat: 0,
                fatSaturated: 0,
                fatUnsaturatedFattyAcids: 0,
                carbohydrate: 0,
                carbohydratePureSugar: 0,
                fiber: 0,
                protein: 0,
                salt: 0
            ),
            favouritedAt: .now
        )
    }
}

private final class FetchFavouriteFoodsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FavouriteFoodDTO] = []
    var queriedCollection: String?
    var queriedOrderByField: String?
    var queriedDescending: Bool?
    var queriedLimit: Int?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }

    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] {
        queriedCollection = collection
        queriedOrderByField = field
        queriedDescending = descending
        queriedLimit = limit
        return stubbedDTOs.compactMap { $0 as? T }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
