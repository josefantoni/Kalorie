//
//  FetchMinimumVersionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 21.10.2026.
//

import XCTest
@testable import Kalorie

final class FetchMinimumVersionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_readsTheAppConfigDocumentFromTheServer() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, "config")
        XCTAssertEqual(dataProvider.queriedId, "app")
    }

    func test_fetch_returnsTheIosField() async throws {
        let (sut, _) = makeSUT(config: AppConfigDTO(minVersionIos: "1.0.1", minVersionAndroid: "9.0"))
        let minimum = try await sut()
        XCTAssertEqual(minimum, AppVersion("1.0.1"))
    }

    func test_fetch_whenDocumentIsMissing_returnsNil() async throws {
        let (sut, _) = makeSUT(config: nil)
        let minimum = try await sut()
        XCTAssertNil(minimum)
    }

    func test_fetch_whenIosFieldIsAbsent_returnsNil() async throws {
        let (sut, _) = makeSUT(config: AppConfigDTO(minVersionIos: nil, minVersionAndroid: "9.0"))
        let minimum = try await sut()
        XCTAssertNil(minimum)
    }

    func test_fetch_whenIosFieldDoesNotParse_returnsNil() async throws {
        let (sut, _) = makeSUT(config: AppConfigDTO(minVersionIos: "1.1-beta", minVersionAndroid: nil))
        let minimum = try await sut()
        XCTAssertNil(minimum)
    }

    func test_fetch_whenReadFails_propagatesTheError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.shouldThrow = true
        do {
            _ = try await sut()
            XCTFail("Expected the read error")
        } catch {}
    }

    // MARK: - Helpers

    private func makeSUT(
        config: AppConfigDTO? = nil
    ) -> (sut: FetchMinimumVersionUseCase, dataProvider: FetchMinimumVersionDataProviderFake) {
        let dataProvider = FetchMinimumVersionDataProviderFake()
        dataProvider.stubbedConfig = config
        let sut = FetchMinimumVersionUseCase(dataProvider: dataProvider)
        return (sut, dataProvider)
    }
}

private final class FetchMinimumVersionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedConfig: AppConfigDTO?
    var shouldThrow = false
    var queriedCollection: String?
    var queriedId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }

    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        if shouldThrow { throw URLError(.notConnectedToInternet) }
        queriedCollection = collection
        queriedId = id
        return stubbedConfig as? T
    }

    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
