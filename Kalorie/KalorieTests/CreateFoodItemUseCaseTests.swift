//
//  CreateFoodItemUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.06.2024.
//

import XCTest
import FirebaseFirestore
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

    func test_createFoodItem_whenItemAlreadyExists_throwsItemAlreadyExistsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let item = makeItem()
        dataProvider.stubbedExistingDTO = FoodItemDTO(item: item)
        do {
            _ = try await sut(item)
            XCTFail("Expected itemAlreadyExists error")
        } catch CreateFoodItemError.itemAlreadyExists {
            // pass
        }
        XCTAssertFalse(dataProvider.didWrite)
    }

    func test_createFoodItem_whenServerUnreachable_throwsUnreachableAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedLoadError = FirestoreDataProviderError.unreachable
        do {
            _ = try await sut(makeItem())
            XCTFail("Expected unreachable error")
        } catch FirestoreDataProviderError.unreachable {
            // pass
        }
        XCTAssertFalse(dataProvider.didWrite)
    }

    func test_createFoodItem_whenWriteIsRejectedByRules_andReReadConfirmsDuplicate_throwsItemAlreadyExists() async throws {
        let (sut, dataProvider) = makeSUT()
        let item = makeItem()
        dataProvider.stubbedSetError = NSError(
            domain: FirestoreErrorDomain,
            code: FirestoreErrorCode.permissionDenied.rawValue
        )
        dataProvider.stubbedConfirmationDTO = FoodItemDTO(item: item)
        do {
            _ = try await sut(item)
            XCTFail("Expected itemAlreadyExists error")
        } catch CreateFoodItemError.itemAlreadyExists {
            // pass
        }
    }

    func test_createFoodItem_whenWriteIsRejectedByRules_andReReadFindsNothing_rethrowsOriginalError() async throws {
        let (sut, dataProvider) = makeSUT()
        let deniedError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue)
        dataProvider.stubbedSetError = deniedError
        do {
            _ = try await sut(makeItem())
            XCTFail("Expected the original permissionDenied error")
        } catch CreateFoodItemError.itemAlreadyExists {
            XCTFail("Should not relabel the failure without confirming a duplicate exists")
        } catch {
            XCTAssertEqual(error as NSError, deniedError)
        }
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

    // MARK: - Properties

    var stubbedExistingDTO: FoodItemDTO?
    var stubbedConfirmationDTO: FoodItemDTO?
    var stubbedLoadError: Error?
    var stubbedSetError: Error?
    var didWrite = false
    private var loadFromServerByIdCallCount = 0

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        if let stubbedLoadError { throw stubbedLoadError }
        loadFromServerByIdCallCount += 1
        return (loadFromServerByIdCallCount == 1 ? stubbedExistingDTO : stubbedConfirmationDTO) as? T
    }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if let stubbedSetError { throw stubbedSetError }
        didWrite = true
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
