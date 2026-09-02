//
//  FetchMealTypesUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
@testable import Kalorie

final class FetchMealTypesUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMealTypes_withEmptyProvider_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchMealTypes_returnsMappedAndSortedDomains() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedMealTypes = [
            MealTypeDTO(id: "1", name: "Oběd", startMinutes: 12 * 60, endMinutes: 14 * 60),
            MealTypeDTO(id: "0", name: "Snídaně", startMinutes: 6 * 60, endMinutes: 9 * 60)
        ]

        let result = try await sut()

        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].name, "Snídaně")
        XCTAssertEqual(result[1].name, "Oběd")
    }

    func test_fetchMealTypes_whenProviderThrowsDecodingError_throwsError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = DecodingError.dataCorrupted(
            DecodingError.Context(codingPath: [], debugDescription: "test")
        )

        do {
            _ = try await sut()
            XCTFail("Expected DecodingError to be thrown")
        } catch is DecodingError {
            // pass
        } catch {
            XCTFail("Expected DecodingError but got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchMealTypesUseCase, dataProvider: FirestoreDataProviderStub) {
        let dataProvider = FirestoreDataProviderStub()
        let sut = FetchMealTypesUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }

    private func makeDate(hour: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: Date()) ?? Date()
    }
}

final class FirestoreDataProviderStub: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedMealTypes: [MealTypeDTO] = []
    var stubbedError: Error?
    var deletedId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if let error = stubbedError { throw error }
        return stubbedMealTypes.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if let error = stubbedError { throw error }
        return stubbedMealTypes.compactMap { $0 as? T }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws { deletedId = id }
}
