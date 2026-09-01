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
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(
        googleSignInProvider: any GoogleSignInProviderProtocol,
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol
    ) {
        self.googleSignInProvider = googleSignInProvider
        self.linkOrMergeCredential = linkOrMergeCredential
        self.authCommandProvider = authCommandProvider
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await googleSignInProvider.signIn()
        try await linkOrMergeCredential(credential: result.credential)
        await saveProfileIfNeeded(displayName: result.displayName, email: result.email)
    }

    func saveProfileIfNeeded(displayName: String?, email: String?) async {
        guard displayName != nil || email != nil else { return }

        if let displayName {
            do {
                try await authCommandProvider.updateDisplayName(displayName)
            } catch {
                Log.error(error, category: Constants.LogCategory.auth)
            }
        }

        guard let userId = authProvider.userId else { return }

        let dto = UserProfileDTO(displayName: displayName, email: email)
        do {
            try await dataProvider.setAsync(dto, id: userId, in: Constants.Firestore.users)
        } catch {
            Log.error(error, category: Constants.LogCategory.auth)
        }
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
