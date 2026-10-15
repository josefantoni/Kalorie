//
//  LinkCredentialUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class LinkCredentialUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_whenSignedIn_linksTheCredential() async throws {
        let (sut, authCommandProvider, _) = makeSUT()

        try await sut(credential: makeCredential())

        XCTAssertEqual(authCommandProvider.linkCallCount, 1)
    }

    func test_callAsFunction_whenAnonymous_throwsNotAuthenticatedWithoutLinking() async {
        let (sut, authCommandProvider, _) = makeSUT(isAnonymous: true)

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected AuthError.notAuthenticated to be thrown")
        } catch AuthError.notAuthenticated {
            XCTAssertEqual(authCommandProvider.linkCallCount, 0, "an anonymous user goes through the merge path, never through this one")
        } catch {
            XCTFail("Expected AuthError.notAuthenticated but got \(error)")
        }
    }

    func test_callAsFunction_whenMergeSnapshotIsPending_throwsMergeInProgressWithoutLinking() async {
        let (sut, authCommandProvider, snapshotStore) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [], favouriteFoods: [], myCreatedMeals: [])

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected LinkCredentialError.mergeInProgress to be thrown")
        } catch LinkCredentialError.mergeInProgress {
            XCTAssertEqual(authCommandProvider.linkCallCount, 0, "an unfinished merge must finish first, or data lands under the wrong uid")
        } catch {
            XCTFail("Expected LinkCredentialError.mergeInProgress but got \(error)")
        }
    }

    func test_callAsFunction_whenProviderAlreadyLinked_throwsAlreadyLinked() async {
        await assertLinkFails(
            with: NSError(domain: AuthErrorDomain, code: AuthErrorCode.providerAlreadyLinked.rawValue),
            mapsTo: LinkCredentialError.alreadyLinked
        )
    }

    func test_callAsFunction_whenCredentialAlreadyInUse_throwsCredentialBelongsToAnotherAccount() async {
        await assertLinkFails(
            with: NSError(domain: AuthErrorDomain, code: AuthErrorCode.credentialAlreadyInUse.rawValue),
            mapsTo: LinkCredentialError.credentialBelongsToAnotherAccount
        )
    }

    func test_callAsFunction_whenEmailAlreadyInUse_throwsEmailBelongsToAnotherAccount() async {
        await assertLinkFails(
            with: NSError(domain: AuthErrorDomain, code: AuthErrorCode.emailAlreadyInUse.rawValue),
            mapsTo: LinkCredentialError.emailBelongsToAnotherAccount
        )
    }

    func test_callAsFunction_whenLinkFailsWithOtherError_propagatesIt() async {
        let (sut, authCommandProvider, _) = makeSUT()
        authCommandProvider.linkError = URLError(.notConnectedToInternet)

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected error to be thrown")
        } catch is URLError {
        } catch {
            XCTFail("Expected URLError but got \(error)")
        }
    }

    // MARK: - Helpers

    private func assertLinkFails(
        with error: Error,
        mapsTo expected: LinkCredentialError,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        let (sut, authCommandProvider, _) = makeSUT()
        authCommandProvider.linkError = error

        do {
            try await sut(credential: makeCredential())
            XCTFail("Expected \(expected) to be thrown", file: file, line: line)
        } catch let linkError as LinkCredentialError {
            XCTAssertEqual("\(linkError)", "\(expected)", file: file, line: line)
        } catch {
            XCTFail("Expected \(expected) but got \(error)", file: file, line: line)
        }
    }

    private func makeSUT(
        isAnonymous: Bool = false
    ) -> (sut: LinkCredentialUseCase, authCommandProvider: AuthCommandProviderFake, snapshotStore: PendingMergeSnapshotStoreFake) {
        let authCommandProvider = AuthCommandProviderFake()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        let sut = LinkCredentialUseCase(
            authProvider: AuthProviderFake(isAnonymous: isAnonymous),
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore
        )
        return (sut, authCommandProvider, snapshotStore)
    }

    private func makeCredential() -> AuthCredential {
        GoogleAuthProvider.credential(withIDToken: "id-token", accessToken: "access-token")
    }
}
