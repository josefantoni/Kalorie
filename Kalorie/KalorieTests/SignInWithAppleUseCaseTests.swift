//
//  SignInWithAppleUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class SignInWithAppleUseCaseTests: XCTestCase {

    // MARK: - saveProfileIfNeeded

    func test_saveProfileIfNeeded_withNameAndEmail_updatesDisplayNameAndSavesProfile() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()
        var fullName = PersonNameComponents()
        fullName.givenName = "Josef"
        fullName.familyName = "Antoni"

        await sut.saveProfileIfNeeded(fullName: fullName, email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertEqual(dataProvider.savedProfile?.displayName, "Josef Antoni")
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com")
    }

    func test_saveProfileIfNeeded_withNilNameAndEmail_doesNothing() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut.saveProfileIfNeeded(fullName: nil, email: nil)

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0)
        XCTAssertNil(dataProvider.savedProfile)
    }

    func test_saveProfileIfNeeded_withEmptyNameComponents_treatsNameAsNilButStillSavesEmail() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut.saveProfileIfNeeded(fullName: PersonNameComponents(), email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0, "prázdné jméno se nesmí uložit jako displayName")
        XCTAssertNil(dataProvider.savedProfile?.displayName)
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com")
    }

    func test_saveProfileIfNeeded_withNameButNoEmail_savesProfileWithNilEmail() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()
        var fullName = PersonNameComponents()
        fullName.givenName = "Josef"

        await sut.saveProfileIfNeeded(fullName: fullName, email: nil)

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertEqual(dataProvider.savedProfile?.displayName, "Josef")
        XCTAssertNil(dataProvider.savedProfile?.email)
    }

    func test_saveProfileIfNeeded_whenNotAuthenticated_updatesDisplayNameButDoesNotSaveProfile() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT(userId: nil)
        var fullName = PersonNameComponents()
        fullName.givenName = "Josef"

        await sut.saveProfileIfNeeded(fullName: fullName, email: nil)

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertNil(dataProvider.savedProfile, "bez userId není kam profil uložit")
    }

    // MARK: - Helpers

    private func makeSUT(
        userId: String? = "user-1"
    ) -> (sut: SignInWithAppleUseCase, authCommandProvider: AuthCommandProviderFake, dataProvider: SignInWithAppleDataProviderFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let dataProvider = SignInWithAppleDataProviderFake()
        let sut = SignInWithAppleUseCase(
            linkOrMergeCredential: LinkOrMergeCredentialUseCaseFake(),
            authCommandProvider: authCommandProvider,
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId)
        )
        return (sut, authCommandProvider, dataProvider)
    }
}

private final class SignInWithAppleDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private(set) var savedProfile: UserProfileDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedProfile = item as? UserProfileDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
