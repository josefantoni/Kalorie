//
//  LinkAppleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

protocol LinkAppleUseCaseProtocol {
    func callAsFunction() async throws
}

struct LinkAppleUseCase: LinkAppleUseCaseProtocol {

    // MARK: - Properties

    private let appleSignInProvider: any AppleSignInProviderProtocol
    private let linkCredential: any LinkCredentialUseCaseProtocol
    private let fillEmptyProfile: any FillEmptyProfileUseCaseProtocol

    // MARK: - Init

    init(
        appleSignInProvider: any AppleSignInProviderProtocol,
        linkCredential: any LinkCredentialUseCaseProtocol,
        fillEmptyProfile: any FillEmptyProfileUseCaseProtocol
    ) {
        self.appleSignInProvider = appleSignInProvider
        self.linkCredential = linkCredential
        self.fillEmptyProfile = fillEmptyProfile
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await appleSignInProvider.signIn()
        try await linkCredential(credential: result.credential)
        let name = result.fullName
            .map { PersonNameComponentsFormatter().string(from: $0) }
            .flatMap { $0.isEmpty ? nil : $0 }
        await fillEmptyProfile(displayName: name, email: result.email)
    }
}

#if DEBUG
struct LinkAppleUseCaseFake: LinkAppleUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
