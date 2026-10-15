//
//  LinkGoogleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

protocol LinkGoogleUseCaseProtocol {
    func callAsFunction() async throws
}

struct LinkGoogleUseCase: LinkGoogleUseCaseProtocol {

    // MARK: - Properties

    private let googleSignInProvider: any GoogleSignInProviderProtocol
    private let linkCredential: any LinkCredentialUseCaseProtocol
    private let fillEmptyProfile: any FillEmptyProfileUseCaseProtocol

    // MARK: - Init

    init(
        googleSignInProvider: any GoogleSignInProviderProtocol,
        linkCredential: any LinkCredentialUseCaseProtocol,
        fillEmptyProfile: any FillEmptyProfileUseCaseProtocol
    ) {
        self.googleSignInProvider = googleSignInProvider
        self.linkCredential = linkCredential
        self.fillEmptyProfile = fillEmptyProfile
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await googleSignInProvider.signIn()
        try await linkCredential(credential: result.credential)
        await fillEmptyProfile(displayName: result.displayName, email: result.email)
    }
}

#if DEBUG
struct LinkGoogleUseCaseFake: LinkGoogleUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
