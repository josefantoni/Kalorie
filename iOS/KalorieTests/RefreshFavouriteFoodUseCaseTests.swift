//
//  RefreshFavouriteFoodUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 19.09.2026.
//

import XCTest
@testable import Kalorie

final class RefreshFavouriteFoodUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_refresh_whenCatalogueItemWasCorrected_returnsCorrectedItemAndRewritesSnapshot() async throws {
        let stored = makeItem(calories: 155)
        let corrected = makeItem(calories: 140)
        let (sut, dataProvider) = makeSUT(catalogue: FoodItemDTO(item: corrected), favourite: FavouriteFoodDTO(item: stored, favouritedAt: Date(timeIntervalSince1970: 1_000)))
        let result = try await sut(stored)
        XCTAssertEqual(result, corrected)
        XCTAssertEqual(dataProvider.savedToCollection, "users/test-user/favouriteFoods")
        XCTAssertEqual(dataProvider.savedDTO?.caloriesPerHundredGrams, 140)
    }

    func test_refresh_keepsOriginalFavouritedAtSoTheOrderingDoesNotJump() async throws {
        let stored = makeItem(calories: 155)
        let (sut, dataProvider) = makeSUT(catalogue: FoodItemDTO(item: makeItem(calories: 140)), favourite: FavouriteFoodDTO(item: stored, favouritedAt: Date(timeIntervalSince1970: 1_000)))
        _ = try await sut(stored)
        XCTAssertEqual(dataProvider.savedDTO?.favouritedAt, 1_000)
    }

    func test_refresh_whenNothingChanged_doesNotWrite() async throws {
        let stored = makeItem(calories: 155)
        let (sut, dataProvider) = makeSUT(catalogue: FoodItemDTO(item: stored), favourite: FavouriteFoodDTO(item: stored, favouritedAt: .now))
        let result = try await sut(stored)
        XCTAssertEqual(result, stored)
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_refresh_whenItemIsNotFromCatalogue_doesNotReadOrWrite() async throws {
        let external = makeItem(kind: .external)
        let (sut, dataProvider) = makeSUT(catalogue: FoodItemDTO(item: makeItem(calories: 1)), favourite: nil)
        let result = try await sut(external)
        XCTAssertEqual(result, external)
        XCTAssertEqual(dataProvider.loadedIds, [])
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_refresh_whenCatalogueItemNoLongerExists_returnsStoredSnapshot() async throws {
        let stored = makeItem()
        let (sut, dataProvider) = makeSUT(catalogue: nil, favourite: FavouriteFoodDTO(item: stored, favouritedAt: .now))
        let result = try await sut(stored)
        XCTAssertEqual(result, stored)
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_refresh_whenFavouriteWasRemovedMeanwhile_returnsFreshItemWithoutRecreatingIt() async throws {
        let corrected = makeItem(calories: 140)
        let (sut, dataProvider) = makeSUT(catalogue: FoodItemDTO(item: corrected), favourite: nil)
        let result = try await sut(makeItem(calories: 155))
        XCTAssertEqual(result, corrected)
        XCTAssertNil(dataProvider.savedDTO)
    }

    func test_refresh_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(catalogue: nil, favourite: nil, userId: nil)
        do {
            _ = try await sut(makeItem())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        catalogue: FoodItemDTO?,
        favourite: FavouriteFoodDTO?,
        userId: String? = "test-user"
    ) -> (sut: RefreshFavouriteFoodUseCase, dataProvider: RefreshFavouriteDataProviderFake) {
        let dataProvider = RefreshFavouriteDataProviderFake(catalogue: catalogue, favourite: favourite)
        let sut = RefreshFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }

    private func makeItem(kind: FoodItemKind = .catalogue, calories: Double = 155) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
            kind: kind,
            czName: "Vejce",
            engName: "Egg",
            weight: 100,
            date: Date(timeIntervalSince1970: 500),
            energyKJ: 648,
            caloriesPerHundredGrams: calories,
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

private final class RefreshFavouriteDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private let catalogue: FoodItemDTO?
    private let favourite: FavouriteFoodDTO?
    private(set) var loadedIds: [String] = []
    private(set) var savedToCollection: String?
    private(set) var savedDTO: FavouriteFoodDTO?

    // MARK: - Init

    init(catalogue: FoodItemDTO?, favourite: FavouriteFoodDTO?) {
        self.catalogue = catalogue
        self.favourite = favourite
    }

    // MARK: - Functions

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        loadedIds.append(collection)
        if collection == Constants.Firestore.foodItems { return catalogue as? T }
        return favourite as? T
    }

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedToCollection = collection
        savedDTO = item as? FavouriteFoodDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
