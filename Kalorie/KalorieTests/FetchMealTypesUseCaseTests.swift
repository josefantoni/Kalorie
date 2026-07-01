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
            MealTypeDTO(id: 1, name: "Oběd", startTime: makeDate(hour: 12).timeIntervalSince1970, endTime: makeDate(hour: 14).timeIntervalSince1970),
            MealTypeDTO(id: 0, name: "Snídaně", startTime: makeDate(hour: 6).timeIntervalSince1970, endTime: makeDate(hour: 9).timeIntervalSince1970)
        ]

        let result = try await sut()

        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].name, "Snídaně")
        XCTAssertEqual(result[1].name, "Oběd")
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
    var deletedId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        stubbedMealTypes.compactMap { $0 as? T }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws { deletedId = id }
}
