//
//  ConfirmMealTypesEmptyUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class ConfirmMealTypesEmptyUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenServerReturnsEmpty_returnsTrue() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertTrue(result)
    }

    func test_callAsFunction_whenServerReturnsMealTypes_returnsFalse() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        let result = try await sut()
        XCTAssertFalse(result)
    }

    func test_callAsFunction_whenServerUnreachable_throwsError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = URLError(.notConnectedToInternet)

        do {
            _ = try await sut()
            XCTFail("Expected error to be thrown")
        } catch {
            // pass
        }
    }

    func test_callAsFunction_usesServerSourceNotCache() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        dataProvider.stubbedCachedMealTypes = []

        let result = try await sut()

        XCTAssertFalse(result, "musí číst ze serveru, ne z (potenciálně zastaralé) cache")
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: ConfirmMealTypesEmptyUseCase, dataProvider: ConfirmMealTypesEmptyDataProviderFake) {
        let dataProvider = ConfirmMealTypesEmptyDataProviderFake()
        let sut = ConfirmMealTypesEmptyUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }
}

private final class ConfirmMealTypesEmptyDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedMealTypes: [MealTypeDTO] = []
    var stubbedCachedMealTypes: [MealTypeDTO] = []
    var stubbedError: Error?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        stubbedCachedMealTypes.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if let stubbedError { throw stubbedError }
        return stubbedMealTypes.compactMap { $0 as? T }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
