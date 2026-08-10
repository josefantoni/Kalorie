//
//  LinkOrMergeCredentialUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class LinkOrMergeCredentialUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenLinkSucceeds_doesNotCallMigrate() async throws {
        let (sut, authCommandProvider, migrateAnonymousData) = makeSUT()

        try await sut(credential: makeCredential())

        XCTAssertEqual(authCommandProvider.linkCallCount, 1)
        XCTAssertEqual(migrateAnonymousData.migrateCallCount, 0)
    }

    func test_callAsFunction_whenCredentialAlreadyInUse_callsMigrateWithAnonymousUserId() async throws {
        let (sut, authCommandProvider, migrateAnonymousData) = makeSUT(anonymousUserId: "anon-123")
        authCommandProvider.linkError = makeCredentialAlreadyInUseError()

        try await sut(credential: makeCredential())

        XCTAssertEqual(migrateAnonymousData.migrateCallCount, 1)
        XCTAssertEqual(migrateAnonymousData.lastSourceUserId, "anon-123")
    }

    func test_callAsFunction_whenLinkFailsWithOtherError_propagatesErrorAndDoesNotCallMigrate() async {
        let (sut, authCommandProvider, migrateAnonymousData) = makeSUT()
        authCommandProvider.linkError = URLError(.notConnectedToInternet)

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected error to be thrown")
        } catch {
            // pass
        }

        XCTAssertEqual(migrateAnonymousData.migrateCallCount, 0)
    }

    func test_callAsFunction_whenEmailAlreadyInUse_throwsAccountExistsWithAnotherProvider() async {
        let (sut, authCommandProvider, migrateAnonymousData) = makeSUT()
        authCommandProvider.linkError = makeEmailAlreadyInUseError()

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected error to be thrown")
        } catch LinkOrMergeCredentialError.accountExistsWithAnotherProvider {
            // pass
        } catch {
            XCTFail("Expected accountExistsWithAnotherProvider, got \(error)")
        }

        XCTAssertEqual(migrateAnonymousData.migrateCallCount, 0, "emailAlreadyInUse steers to Apple, it does not merge")
    }

    // MARK: - Helpers

    private func makeSUT(
        anonymousUserId: String? = "anon-123"
    ) -> (sut: LinkOrMergeCredentialUseCase, authCommandProvider: AuthCommandProviderFake, migrateAnonymousData: MigrateAnonymousDataUseCaseFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let migrateAnonymousData = MigrateAnonymousDataUseCaseFake()
        let sut = LinkOrMergeCredentialUseCase(
            authProvider: AuthProviderFake(userId: anonymousUserId),
            authCommandProvider: authCommandProvider,
            migrateAnonymousData: migrateAnonymousData
        )
        return (sut, authCommandProvider, migrateAnonymousData)
    }

    private func makeCredential() -> AuthCredential {
        EmailAuthProvider.credential(withEmail: "test@example.com", password: "password")
    }

    private func makeCredentialAlreadyInUseError() -> NSError {
        NSError(domain: AuthErrorDomain, code: AuthErrorCode.credentialAlreadyInUse.rawValue)
    }

    private func makeEmailAlreadyInUseError() -> NSError {
        NSError(domain: AuthErrorDomain, code: AuthErrorCode.emailAlreadyInUse.rawValue)
    }
}
