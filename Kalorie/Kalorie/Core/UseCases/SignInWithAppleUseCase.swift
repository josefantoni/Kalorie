//
//  SignInWithAppleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import FirebaseAuth
import Foundation

protocol SignInWithAppleUseCaseProtocol {
    func callAsFunction() async throws
}

struct SignInWithAppleUseCase: SignInWithAppleUseCaseProtocol {

    // MARK: - Properties

    private let appleSignInProvider: any AppleSignInProviderProtocol
    private let linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(
        appleSignInProvider: any AppleSignInProviderProtocol,
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol
    ) {
        self.appleSignInProvider = appleSignInProvider
        self.linkOrMergeCredential = linkOrMergeCredential
        self.authCommandProvider = authCommandProvider
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws {
        let result = try await appleSignInProvider.signIn()

        let credential = OAuthProvider.appleCredential(
            withIDToken: result.identityToken,
            rawNonce: result.rawNonce,
            fullName: result.fullName
        )

        try await linkOrMergeCredential(credential: credential)

        await saveProfileIfNeeded(fullName: result.fullName, email: result.email)
    }

    func saveProfileIfNeeded(fullName: PersonNameComponents?, email: String?) async {
        let name = fullName.flatMap { components -> String? in
            let formatted = PersonNameComponentsFormatter().string(from: components)
            return formatted.isEmpty ? nil : formatted
        }

        guard name != nil || email != nil else { return }

        if let name {
            do {
                try await authCommandProvider.updateDisplayName(name)
            } catch {
                Log.error(error, category: Constants.LogCategory.auth)
            }
        }

        if let userId = authProvider.userId {
            let dto = UserProfileDTO(displayName: name, email: email)
            do {
                try await dataProvider.setAsync(dto, id: userId, in: Constants.Firestore.users)
            } catch {
                Log.error(error, category: Constants.LogCategory.auth)
            }
        }
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
