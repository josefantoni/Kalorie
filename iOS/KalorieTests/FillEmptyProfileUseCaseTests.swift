//
//  FillEmptyProfileUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import XCTest
@testable import Kalorie

final class FillEmptyProfileUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenProfileIsEmpty_fillsNameAndEmail() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut(displayName: "Josef Antoni", email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertEqual(dataProvider.savedProfile?.displayName, "Josef Antoni")
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com")
    }

    func test_callAsFunction_whenAccountAlreadyHasADisplayName_doesNotOverwriteIt() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT(
            authProvider: AuthProviderFake(isAnonymous: false, displayName: "Josef")
        )
        dataProvider.stubbedProfile = UserProfileDTO(displayName: "Josef", email: nil)

        await sut(displayName: "Apple Name", email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0, "linking a second provider must not replace the name the user already has")
        XCTAssertEqual(dataProvider.savedProfile?.displayName, "Josef")
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com", "an empty field is still filled")
    }

    func test_callAsFunction_whenProfileAlreadyHasNameAndEmail_writesNothing() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT(
            authProvider: AuthProviderFake(isAnonymous: false, displayName: "Josef")
        )
        dataProvider.stubbedProfile = UserProfileDTO(displayName: "Josef", email: "josef@example.com")

        await sut(displayName: "Other", email: "other@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0)
        XCTAssertNil(dataProvider.savedProfile)
    }

    func test_callAsFunction_withNothingToFill_writesNothing() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut(displayName: nil, email: "")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0)
        XCTAssertNil(dataProvider.savedProfile)
    }

    func test_callAsFunction_whenProfileCannotBeRead_writesNothing() async {
        let (sut, _, dataProvider) = makeSUT()
        dataProvider.loadError = URLError(.notConnectedToInternet)

        await sut(displayName: "Josef", email: "josef@example.com")

        XCTAssertNil(dataProvider.savedProfile, "without knowing what is stored, a write could overwrite it")
    }

    // MARK: - Helpers

    private func makeSUT(
        authProvider: AuthProviderFake = AuthProviderFake(isAnonymous: false)
    ) -> (sut: FillEmptyProfileUseCase, authCommandProvider: AuthCommandProviderFake, dataProvider: FillEmptyProfileDataProviderFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let dataProvider = FillEmptyProfileDataProviderFake()
        let sut = FillEmptyProfileUseCase(
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            dataProvider: dataProvider
        )
        return (sut, authCommandProvider, dataProvider)
    }
}

private final class FillEmptyProfileDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedProfile: UserProfileDTO?
    var loadError: Error?
    private(set) var savedProfile: UserProfileDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        if let loadError { throw loadError }
        return stubbedProfile as? T
    }

    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedProfile = item as? UserProfileDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
