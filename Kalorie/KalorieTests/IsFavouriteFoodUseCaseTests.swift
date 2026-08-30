//
//  IsFavouriteFoodUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class IsFavouriteFoodUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_isFavouriteFood_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(id: "12345")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_isFavouriteFood_whenDocumentExists_returnsTrue() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTO = FavouriteFoodDTO(
            item: FoodItemDomain(
                id: "12345",
                kind: .catalogue,
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
            ),
            favouritedAt: .now
        )
        let result = try await sut(id: "12345")
        XCTAssertTrue(result)
    }

    func test_isFavouriteFood_whenDocumentMissing_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(id: "12345")
        XCTAssertFalse(result)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: IsFavouriteFoodUseCase, dataProvider: IsFavouriteFoodDataProviderFake) {
        let dataProvider = IsFavouriteFoodDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = IsFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class IsFavouriteFoodDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTO: FavouriteFoodDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? {
        stubbedDTO as? T
    }

    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
