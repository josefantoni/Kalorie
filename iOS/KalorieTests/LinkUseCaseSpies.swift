//
//  LinkUseCaseSpies.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.10.2026.
//

import FirebaseAuth
@testable import Kalorie

final class LinkCredentialSpy: LinkCredentialUseCaseProtocol {

    // MARK: - Properties

    private let errorToThrow: Error?
    private(set) var callCount = 0

    // MARK: - Init

    init(errorToThrow: Error? = nil) {
        self.errorToThrow = errorToThrow
    }

    // MARK: - Functions

    func callAsFunction(credential: AuthCredential) async throws {
        callCount += 1
        if let errorToThrow { throw errorToThrow }
    }
}

final class FillEmptyProfileSpy: FillEmptyProfileUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    private(set) var receivedDisplayName: String?
    private(set) var receivedEmail: String?

    // MARK: - Functions

    func callAsFunction(displayName: String?, email: String?) async {
        callCount += 1
        receivedDisplayName = displayName
        receivedEmail = email
    }
}
