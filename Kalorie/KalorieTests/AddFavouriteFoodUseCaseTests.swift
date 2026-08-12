//
//  AddFavouriteFoodUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class AddFavouriteFoodUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_addFavouriteFood_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeItem())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_addFavouriteFood_setsDocumentIdToBarcode() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(makeItem(id: "12345"))
        XCTAssertEqual(dataProvider.savedToCollection, "users/user-123/favouriteFoods")
        XCTAssertEqual(dataProvider.savedDocumentId, "12345")
        XCTAssertEqual(dataProvider.savedDTO?.id, "12345")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: AddFavouriteFoodUseCase, dataProvider: AddFavouriteFoodDataProviderFake) {
        let dataProvider = AddFavouriteFoodDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = AddFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeItem(id: String = "12345") -> FoodItemDomain {
        FoodItemDomain(
            id: id,
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

private final class AddFavouriteFoodDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedToCollection: String?
    var savedDTO: FavouriteFoodDTO?
    var savedDocumentId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedToCollection = collection
        savedDTO = item as? FavouriteFoodDTO
        savedDocumentId = id
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
