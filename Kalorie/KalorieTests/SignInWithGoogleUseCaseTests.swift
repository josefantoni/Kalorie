//
//  SignInWithGoogleUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.08.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class SignInWithGoogleUseCaseTests: XCTestCase {

    // MARK: - saveProfileIfNeeded

    func test_saveProfileIfNeeded_withNameAndEmail_updatesDisplayNameAndSavesProfile() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut.saveProfileIfNeeded(displayName: "Josef Antoni", email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertEqual(dataProvider.savedProfile?.displayName, "Josef Antoni")
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com")
    }

    func test_saveProfileIfNeeded_withNilNameAndEmail_doesNothing() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut.saveProfileIfNeeded(displayName: nil, email: nil)

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0)
        XCTAssertNil(dataProvider.savedProfile)
    }

    func test_saveProfileIfNeeded_withEmailOnly_doesNotUpdateDisplayNameButSavesProfile() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT()

        await sut.saveProfileIfNeeded(displayName: nil, email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 0, "bez jména není co do Auth profilu zapisovat")
        XCTAssertNil(dataProvider.savedProfile?.displayName)
        XCTAssertEqual(dataProvider.savedProfile?.email, "josef@example.com")
    }

    func test_saveProfileIfNeeded_whenNotAuthenticated_updatesDisplayNameButDoesNotSaveProfile() async {
        let (sut, authCommandProvider, dataProvider) = makeSUT(userId: nil)

        await sut.saveProfileIfNeeded(displayName: "Josef Antoni", email: "josef@example.com")

        XCTAssertEqual(authCommandProvider.updateDisplayNameCallCount, 1)
        XCTAssertNil(dataProvider.savedProfile, "bez userId není kam profil uložit")
    }

    // MARK: - callAsFunction

    func test_callAsFunction_whenProviderFails_propagatesError() async {
        let (sut, _, _) = makeSUT(googleSignInProvider: GoogleSignInProviderFake(errorToThrow: URLError(.notConnectedToInternet)))

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch {
            // pass
        }
    }

    func test_callAsFunction_passesCredentialFromProviderToLinkOrMergeCredential() async throws {
        let stubbedResult = GoogleSignInResult(
            credential: GoogleAuthProvider.credential(withIDToken: "id-token", accessToken: "access-token"),
            displayName: nil,
            email: nil
        )
        let linkOrMergeCredential = LinkOrMergeCredentialCapturingFake()
        let (sut, _, _) = makeSUT(
            googleSignInProvider: GoogleSignInProviderFake(result: stubbedResult),
            linkOrMergeCredential: linkOrMergeCredential
        )

        try await sut()

        XCTAssertEqual(linkOrMergeCredential.callCount, 1)
        XCTAssertEqual(linkOrMergeCredential.receivedCredential?.provider, "google.com")
    }

    // MARK: - Helpers

    private func makeSUT(
        googleSignInProvider: any GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol = LinkOrMergeCredentialUseCaseFake(),
        userId: String? = "user-1"
    ) -> (sut: SignInWithGoogleUseCase, authCommandProvider: AuthCommandProviderFake, dataProvider: SignInWithGoogleDataProviderFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let dataProvider = SignInWithGoogleDataProviderFake()
        let sut = SignInWithGoogleUseCase(
            googleSignInProvider: googleSignInProvider,
            linkOrMergeCredential: linkOrMergeCredential,
            authCommandProvider: authCommandProvider,
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId)
        )
        return (sut, authCommandProvider, dataProvider)
    }
}

private final class SignInWithGoogleDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private(set) var savedProfile: UserProfileDTO?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedProfile = item as? UserProfileDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}

private final class LinkOrMergeCredentialCapturingFake: LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    private(set) var receivedCredential: AuthCredential?

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        callCount += 1
        receivedCredential = credential
    }
}
