//
//  FetchMyCreatedMealsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 20.08.2026.
//

import XCTest
@testable import Kalorie

final class FetchMyCreatedMealsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMyCreatedMeals_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetchMyCreatedMeals_queriesUserSpecificCollectionOrderedByUpdatedAtDescending() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, "users/user-123/myCreatedMeals")
        XCTAssertEqual(dataProvider.queriedOrderByField, "updated_at")
        XCTAssertEqual(dataProvider.queriedDescending, true)
        XCTAssertEqual(dataProvider.queriedLimit, 50)
    }

    func test_fetchMyCreatedMeals_mapsStubbedDTOsToDomains() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [makeDTO(id: "meal-1", name: "Kaše")]
        let result = try await sut()
        XCTAssertEqual(result.map(\.id), ["meal-1"])
        XCTAssertEqual(result.map(\.name), ["Kaše"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchMyCreatedMealsUseCase, dataProvider: FetchMyCreatedMealsDataProviderFake) {
        let dataProvider = FetchMyCreatedMealsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchMyCreatedMealsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(id: String, name: String) -> MyCreatedMealDTO {
        MyCreatedMealDTO(meal: MyCreatedMealDomain(id: id, name: name, ingredients: [makeIngredient()], createdAt: .now, updatedAt: .now))
    }

    private func makeIngredient() -> MyCreatedMealIngredientDomain {
        MyCreatedMealIngredientDomain(
            foodItemId: "12345",
            czName: "Ovesné vločky",
            engName: "Oats",
            grams: 50,
            nutrition: FoodNutritionValues(
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
        )
    }
}

private final class FetchMyCreatedMealsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [MyCreatedMealDTO] = []
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
