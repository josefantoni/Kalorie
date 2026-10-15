//
//  LinkAppleUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import FirebaseAuth
import XCTest
@testable import Kalorie

final class LinkAppleUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_linksTheAppleCredentialThenFillsTheProfile() async throws {
        let linkCredential = LinkCredentialSpy()
        let fillEmptyProfile = FillEmptyProfileSpy()
        var fullName = PersonNameComponents()
        fullName.givenName = "Josef"
        fullName.familyName = "Antoni"
        let sut = makeSUT(
            appleSignInProvider: makeAppleSignInProvider(fullName: fullName, email: "josef@example.com"),
            linkCredential: linkCredential,
            fillEmptyProfile: fillEmptyProfile
        )

        try await sut()

        XCTAssertEqual(linkCredential.callCount, 1)
        XCTAssertEqual(fillEmptyProfile.receivedDisplayName, "Josef Antoni")
        XCTAssertEqual(fillEmptyProfile.receivedEmail, "josef@example.com")
    }

    func test_callAsFunction_whenLinkFails_propagatesErrorAndLeavesTheProfileAlone() async {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(
            linkCredential: LinkCredentialSpy(errorToThrow: LinkCredentialError.credentialBelongsToAnotherAccount),
            fillEmptyProfile: fillEmptyProfile
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch LinkCredentialError.credentialBelongsToAnotherAccount {
            XCTAssertEqual(fillEmptyProfile.callCount, 0, "a refused link must not touch the signed-in account's profile")
        } catch {
            XCTFail("Expected credentialBelongsToAnotherAccount but got \(error)")
        }
    }

    func test_callAsFunction_whenAppleSignInFails_doesNotLink() async {
        let linkCredential = LinkCredentialSpy()
        let sut = makeSUT(
            appleSignInProvider: AppleSignInProviderFake(errorToThrow: AppleSignInError.signInAlreadyInProgress),
            linkCredential: linkCredential
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch AppleSignInError.signInAlreadyInProgress {
            XCTAssertEqual(linkCredential.callCount, 0)
        } catch {
            XCTFail("Expected signInAlreadyInProgress but got \(error)")
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
        linkCredential: LinkCredentialSpy = LinkCredentialSpy(),
        fillEmptyProfile: FillEmptyProfileSpy = FillEmptyProfileSpy()
    ) -> LinkAppleUseCase {
        LinkAppleUseCase(
            appleSignInProvider: appleSignInProvider,
            linkCredential: linkCredential,
            fillEmptyProfile: fillEmptyProfile
        )
    }
}
