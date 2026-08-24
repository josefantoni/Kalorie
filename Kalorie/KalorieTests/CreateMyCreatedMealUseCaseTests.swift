//
//  CreateMyCreatedMealUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 20.08.2026.
//

import XCTest
import MacroKit
@testable import Kalorie

final class CreateMyCreatedMealUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_createMyCreatedMeal_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(name: "Kaše", ingredients: [makeIngredient()])
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_createMyCreatedMeal_mintsIdAndStampsCreatedAtEqualToUpdatedAt_andWritesToUserCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        let meal = try await sut(name: "Kaše", ingredients: [makeIngredient()])
        XCTAssertFalse(meal.id.isEmpty)
        XCTAssertEqual(meal.createdAt, meal.updatedAt)
        XCTAssertEqual(dataProvider.savedToCollection, "users/user-123/myCreatedMeals")
        XCTAssertEqual(dataProvider.savedDocumentId, meal.id)
    }

    func test_createMyCreatedMeal_trimsName() async throws {
        let (sut, _) = makeSUT()
        let meal = try await sut(name: "  Kaše  ", ingredients: [makeIngredient()])
        XCTAssertEqual(meal.name, "Kaše")
    }

    func test_validation_canSaveAgreesWithUseCaseAcceptance_acrossTheSameCases() async throws {
        let cases: [(name: String, ingredients: [MyCreatedMealIngredientDomain], isValid: Bool)] = [
            ("Ovesná kaše", [makeIngredient()], true),
            ("", [makeIngredient()], false),
            ("   ", [makeIngredient()], false),
            ("Ovesná kaše", [], false),
            ("Ovesná kaše", [makeIngredient(grams: 0.5)], false)
        ]
        for testCase in cases {
            XCTAssertEqual(
                MyCreatedMealValidation.canSave(name: testCase.name, ingredients: testCase.ingredients),
                testCase.isValid,
                "canSave mismatch for '\(testCase.name)'"
            )
            let (sut, _) = makeSUT()
            do {
                _ = try await sut(name: testCase.name, ingredients: testCase.ingredients)
                XCTAssertTrue(testCase.isValid, "use case accepted a case canSave rejects: '\(testCase.name)'")
            } catch {
                XCTAssertFalse(testCase.isValid, "use case rejected a case canSave accepts: '\(testCase.name)'")
            }
        }
    }

    func test_asFoodItem_composesFractionalDensity_thatRoundsOnceAtLogTime() async throws {
        let (sut, _) = makeSUT()
        let ingredients = [
            makeIngredient(caloriesPerHundredGrams: 133.6, grams: 150),
            makeIngredient(caloriesPerHundredGrams: 90.4, grams: 50)
        ]
        let meal = try await sut(name: "Míchaná kaše", ingredients: ingredients)
        let foodItem = meal.asFoodItem()
        XCTAssertEqual(foodItem.weight, 200)
        XCTAssertEqual(foodItem.caloriesPerHundredGrams, 122.8, accuracy: 0.0001)
        let loggedCalories = MacrosKt.scaledCalories(
            caloriesPerHundredGrams: foodItem.caloriesPerHundredGrams,
            ratio: foodItem.weight / 100
        )
        XCTAssertEqual(loggedCalories, 246)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: CreateMyCreatedMealUseCase, dataProvider: CreateMyCreatedMealDataProviderFake) {
        let dataProvider = CreateMyCreatedMealDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = CreateMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeIngredient(caloriesPerHundredGrams: Double = 155, grams: Double = 50) -> MyCreatedMealIngredientDomain {
        MyCreatedMealIngredientDomain(
            foodItemId: "12345",
            czName: "Ovesné vločky",
            engName: "Oats",
            grams: grams,
            nutrition: FoodNutritionValues(
                energyKJ: 648,
                caloriesPerHundredGrams: caloriesPerHundredGrams,
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

private final class CreateMyCreatedMealDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedToCollection: String?
    var savedDTO: MyCreatedMealDTO?
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
        savedDTO = item as? MyCreatedMealDTO
        savedDocumentId = id
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
