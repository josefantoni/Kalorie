//
//  SignInWithAppleUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class SignInWithAppleUseCaseTests: XCTestCase {

    func test_callAsFunction_afterLinkingOrMerging_fillsTheEmptyProfileFieldsWithTheFormattedNameAndEmail() async throws {
        var fullName = PersonNameComponents()
        fullName.givenName = "Josef"
        fullName.familyName = "Antoni"
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(
            appleSignInProvider: makeAppleSignInProvider(fullName: fullName, email: "josef@example.com"),
            fillEmptyProfile: fillEmptyProfile
        )

        try await sut()

        XCTAssertEqual(fillEmptyProfile.callCount, 1, "an existing profile of the merged account must be filled, never replaced")
        XCTAssertEqual(fillEmptyProfile.receivedDisplayName, "Josef Antoni")
        XCTAssertEqual(fillEmptyProfile.receivedEmail, "josef@example.com")
    }

    func test_callAsFunction_withEmptyNameComponents_passesNilNameSoItCannotBlankTheStoredOne() async throws {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(
            appleSignInProvider: makeAppleSignInProvider(fullName: PersonNameComponents(), email: "josef@example.com"),
            fillEmptyProfile: fillEmptyProfile
        )

        try await sut()

        XCTAssertNil(fillEmptyProfile.receivedDisplayName)
        XCTAssertEqual(fillEmptyProfile.receivedEmail, "josef@example.com")
    }

    func test_callAsFunction_whenLinkOrMergeFails_propagatesErrorAndLeavesTheProfileAlone() async {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(
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
        let sut = makeSUT(appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress))

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch {
            // pass
        }
    }

    // MARK: - Helpers

    private func makeAppleSignInProvider(fullName: PersonNameComponents?, email: String?) -> AppleSignInProviderFake {
        AppleSignInProviderFake(
            result: AppleSignInResult(
                credential: OAuthProvider.appleCredential(withIDToken: "token", rawNonce: "nonce", fullName: fullName),
                fullName: fullName,
                email: email,
                authorizationCode: nil
            )
        )
    }

    private func makeSUT(
        appleSignInProvider: AppleSignInProviderFake = AppleSignInProviderFake(),
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol = LinkOrMergeCredentialUseCaseFake(),
        fillEmptyProfile: FillEmptyProfileSpy = FillEmptyProfileSpy()
    ) -> SignInWithAppleUseCase {
        SignInWithAppleUseCase(
            appleSignInProvider: appleSignInProvider,
            linkOrMergeCredential: linkOrMergeCredential,
            fillEmptyProfile: fillEmptyProfile
        )
    }
}
