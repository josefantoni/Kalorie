//
//  SignInWithAppleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation

protocol SignInWithAppleUseCaseProtocol {
    func callAsFunction() async throws
}

struct SignInWithAppleUseCase: SignInWithAppleUseCaseProtocol {

    // MARK: - Properties

    private let appleSignInProvider: any AppleSignInProviderProtocol
    private let linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol
    private let fillEmptyProfile: any FillEmptyProfileUseCaseProtocol

    // MARK: - Init

    init(
        appleSignInProvider: any AppleSignInProviderProtocol,
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol,
        fillEmptyProfile: any FillEmptyProfileUseCaseProtocol
    ) {
        self.appleSignInProvider = appleSignInProvider
        self.linkOrMergeCredential = linkOrMergeCredential
        self.fillEmptyProfile = fillEmptyProfile
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await appleSignInProvider.signIn()
        try await linkOrMergeCredential(credential: result.credential)
        let name = result.fullName
            .map { PersonNameComponentsFormatter().string(from: $0) }
            .flatMap { $0.isEmpty ? nil : $0 }
        await fillEmptyProfile(displayName: name, email: result.email)
    }
}

#if DEBUG
struct SignInWithAppleUseCaseFake: SignInWithAppleUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
