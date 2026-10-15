//
//  SignInWithGoogleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.08.2026.
//

import FirebaseAuth
import Foundation

protocol SignInWithGoogleUseCaseProtocol {
    func callAsFunction() async throws
}

struct SignInWithGoogleUseCase: SignInWithGoogleUseCaseProtocol {

    // MARK: - Properties

    private let googleSignInProvider: any GoogleSignInProviderProtocol
    private let linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol
    private let fillEmptyProfile: any FillEmptyProfileUseCaseProtocol

    // MARK: - Init

    init(
        googleSignInProvider: any GoogleSignInProviderProtocol,
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol,
        fillEmptyProfile: any FillEmptyProfileUseCaseProtocol
    ) {
        self.googleSignInProvider = googleSignInProvider
        self.linkOrMergeCredential = linkOrMergeCredential
        self.fillEmptyProfile = fillEmptyProfile
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await googleSignInProvider.signIn()
        try await linkOrMergeCredential(credential: result.credential)
        await fillEmptyProfile(displayName: result.displayName, email: result.email)
    }
}

#if DEBUG
struct SignInWithGoogleUseCaseFake: SignInWithGoogleUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction() async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
