//
//  SignInWithAppleUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import FirebaseAuth
import Foundation

enum SignInWithAppleError: Error {
    case invalidCredential
}

protocol SignInWithAppleUseCaseProtocol {
    func callAsFunction(authorization: ASAuthorization, nonce: String) async throws
}

struct SignInWithAppleUseCase: SignInWithAppleUseCaseProtocol {

    // MARK: - Properties

    private let linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(
        linkOrMergeCredential: any LinkOrMergeCredentialUseCaseProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol
    ) {
        self.linkOrMergeCredential = linkOrMergeCredential
        self.authCommandProvider = authCommandProvider
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(authorization: ASAuthorization, nonce: String) async throws {
        guard
            let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let identityToken = appleIDCredential.identityToken,
            let idTokenString = String(data: identityToken, encoding: .utf8)
        else { throw SignInWithAppleError.invalidCredential }

        let credential = OAuthProvider.appleCredential(
            withIDToken: idTokenString,
            rawNonce: nonce,
            fullName: appleIDCredential.fullName
        )

        try await linkOrMergeCredential(credential: credential)

        await saveProfileIfNeeded(fullName: appleIDCredential.fullName, email: appleIDCredential.email)
    }

    func saveProfileIfNeeded(fullName: PersonNameComponents?, email: String?) async {
        let name = fullName.flatMap { components -> String? in
            let formatted = PersonNameComponentsFormatter().string(from: components)
            return formatted.isEmpty ? nil : formatted
        }

        guard name != nil || email != nil else { return }

        if let name {
            try? await authCommandProvider.updateDisplayName(name)
        }

        if let userId = authProvider.userId {
            let dto = UserProfileDTO(displayName: name, email: email)
            try? await dataProvider.setAsync(dto, id: userId, in: Constants.Firestore.users)
        }
    }
}

#if DEBUG
struct SignInWithAppleUseCaseFake: SignInWithAppleUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(authorization: ASAuthorization, nonce: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
