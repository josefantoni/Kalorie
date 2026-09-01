//
//  CreateFoodItemUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.06.2024.
//

import XCTest
@testable import Kalorie

final class CreateFoodItemUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_createFoodItem_withInvalidCode_throwsInvalidCodeError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(makeItem(id: ""))
            XCTFail("Expected invalidCode error")
        } catch CreateFoodItemError.invalidCode {
            // pass
        }
    }

    func test_createFoodItem_withCodeOfInvalidLength_throwsInvalidCodeError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(makeItem(id: "123456789"))
            XCTFail("Expected invalidCode error")
        } catch CreateFoodItemError.invalidCode {
            // pass
        }
    }

    func test_createFoodItem_withNonASCIIDigits_throwsInvalidCodeError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(makeItem(id: "١٢٣٤٥٦٧٨"))
            XCTFail("Expected invalidCode error")
        } catch CreateFoodItemError.invalidCode {
            // pass
        }
    }

    func test_createFoodItem_withEmptyName_throwsInvalidNameError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(makeItem(name: ""))
            XCTFail("Expected invalidName error")
        } catch CreateFoodItemError.invalidName {
            // pass
        }
    }

    func test_createFoodItem_withZeroCalories_throwsInvalidCaloriesError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(makeItem(caloriesPerHundredGrams: 0))
            XCTFail("Expected invalidCalories error")
        } catch CreateFoodItemError.invalidCalories {
            // pass
        }
    }

    func test_createFoodItem_withValidInput_returnsNewFoodItem() async throws {
        let (sut, _) = makeSUT()
        let item = makeItem()
        let result = try await sut(item)
        XCTAssertEqual(result.czName, item.czName)
        XCTAssertEqual(result.caloriesPerHundredGrams, item.caloriesPerHundredGrams)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: CreateFoodItemUseCase, dataProvider: FirestoreDataProviderFake) {
        let dataProvider = FirestoreDataProviderFake()
        let sut = CreateFoodItemUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }

    private func makeItem(
        id: String = "12345678",
        name: String = "Tvaroh",
        weight: Double = 200,
        caloriesPerHundredGrams: Double = 80
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: name,
            engName: "Cottage cheese",
            weight: weight,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}

final class FirestoreDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
