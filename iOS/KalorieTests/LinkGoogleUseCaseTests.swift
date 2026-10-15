//
//  LinkGoogleUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import XCTest
@testable import Kalorie

final class LinkGoogleUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_linksTheGoogleCredentialThenFillsTheProfile() async throws {
        let linkCredential = LinkCredentialSpy()
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(linkCredential: linkCredential, fillEmptyProfile: fillEmptyProfile)

        try await sut()

        XCTAssertEqual(linkCredential.callCount, 1)
        XCTAssertEqual(fillEmptyProfile.receivedDisplayName, "Josef Antoni")
        XCTAssertEqual(fillEmptyProfile.receivedEmail, "josef@example.com")
    }

    func test_callAsFunction_whenLinkFails_propagatesErrorAndLeavesTheProfileAlone() async {
        let fillEmptyProfile = FillEmptyProfileSpy()
        let sut = makeSUT(
            linkCredential: LinkCredentialSpy(errorToThrow: LinkCredentialError.emailBelongsToAnotherAccount),
            fillEmptyProfile: fillEmptyProfile
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch LinkCredentialError.emailBelongsToAnotherAccount {
            XCTAssertEqual(fillEmptyProfile.callCount, 0, "a refused link must not touch the signed-in account's profile")
        } catch {
            XCTFail("Expected emailBelongsToAnotherAccount but got \(error)")
        }
    }

    func test_callAsFunction_whenGoogleSignInFails_doesNotLink() async {
        let linkCredential = LinkCredentialSpy()
        let sut = makeSUT(
            googleSignInProvider: GoogleSignInProviderFake(errorToThrow: GoogleSignInError.missingIDToken),
            linkCredential: linkCredential
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch GoogleSignInError.missingIDToken {
            XCTAssertEqual(linkCredential.callCount, 0)
        } catch {
            XCTFail("Expected missingIDToken but got \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        googleSignInProvider: GoogleSignInProviderFake = GoogleSignInProviderFake(),
        linkCredential: LinkCredentialSpy = LinkCredentialSpy(),
        fillEmptyProfile: FillEmptyProfileSpy = FillEmptyProfileSpy()
    ) -> LinkGoogleUseCase {
        LinkGoogleUseCase(
            googleSignInProvider: googleSignInProvider,
            linkCredential: linkCredential,
            fillEmptyProfile: fillEmptyProfile
        )
    }
}
