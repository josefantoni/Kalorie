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

    func test_callAsFunction_afterLinkingOrMerging_fillsTheEmptyProfileFieldsWithTheNameAndEmail() async throws {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let (sut, _) = makeSUT(
            googleSignInProvider: GoogleSignInProviderFake(result: makeResult(displayName: "Josef Antoni", email: "josef@example.com")),
            fillEmptyProfile: fillEmptyProfile
        )

        try await sut()

        XCTAssertEqual(fillEmptyProfile.callCount, 1, "an existing profile of the merged account must be filled, never replaced")
        XCTAssertEqual(fillEmptyProfile.receivedDisplayName, "Josef Antoni")
        XCTAssertEqual(fillEmptyProfile.receivedEmail, "josef@example.com")
    }

    func test_callAsFunction_whenLinkOrMergeFails_propagatesErrorAndLeavesTheProfileAlone() async {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let (sut, _) = makeSUT(
            linkOrMergeCredential: LinkOrMergeCredentialUseCaseFake(shouldThrow: true),
            fillEmptyProfile: fillEmptyProfile
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(fillEmptyProfile.callCount, 0)
        }
    }

    func test_callAsFunction_whenProviderFails_propagatesError() async {
        let (sut, _) = makeSUT(googleSignInProvider: GoogleSignInProviderFake(errorToThrow: URLError(.notConnectedToInternet)))

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
        let (sut, _) = makeSUT(
            googleSignInProvider: GoogleSignInProviderFake(result: stubbedResult),
            linkOrMergeCredential: linkOrMergeCredential
        )

        try await sut()

        XCTAssertEqual(linkOrMergeCredential.callCount, 1)
        XCTAssertEqual(linkOrMergeCredential.receivedCredential?.provider, "google.com")
    }

    // MARK: - Helpers

    private func makeResult(displayName: String?, email: String?) -> GoogleSignInResult {
        GoogleSignInResult(
            credential: GoogleAuthProvider.credential(withIDToken: "id-token", accessToken: "access-token"),
            displayName: displayName,
            email: email
        )
    }

    private func makeSUT(
        googleSignInProvider: any GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol = LinkOrMergeCredentialUseCaseFake(),
        fillEmptyProfile: FillEmptyProfileSpy = FillEmptyProfileSpy()
    ) -> (sut: SignInWithGoogleUseCase, fillEmptyProfile: FillEmptyProfileSpy) {
        let sut = SignInWithGoogleUseCase(
            googleSignInProvider: googleSignInProvider,
            linkOrMergeCredential: linkOrMergeCredential,
            fillEmptyProfile: fillEmptyProfile
        )
        return (sut, fillEmptyProfile)
    }
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
