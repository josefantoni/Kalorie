//
//  GoogleSignInProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.08.2026.
//

import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import UIKit

enum GoogleSignInError: Error {
    case missingConfiguration
    case noPresentingViewController
    case missingIDToken
}

struct GoogleSignInResult {
    let credential: AuthCredential
    let displayName: String?
    let email: String?
}

@MainActor
protocol GoogleSignInProviderProtocol {
    func signIn() async throws -> GoogleSignInResult
}

struct GoogleSignInProvider: GoogleSignInProviderProtocol {

    // MARK: - Init

    // swiftlint:disable:next unneeded_synthesized_initializer
    nonisolated init() {}

    // MARK: - Functions

    func signIn() async throws -> GoogleSignInResult {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            throw GoogleSignInError.missingConfiguration
        }

        guard
            let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
            let rootViewController = scene.keyWindow?.rootViewController
        else { throw GoogleSignInError.noPresentingViewController }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController)

        guard let idToken = result.user.idToken?.tokenString else {
            throw GoogleSignInError.missingIDToken
        }

        return GoogleSignInResult(
            credential: GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            ),
            displayName: result.user.profile?.name,
            email: result.user.profile?.email
        )
    }
}

#if DEBUG
struct GoogleSignInProviderFake: GoogleSignInProviderProtocol {

    // MARK: - Properties

    var result: GoogleSignInResult
    var errorToThrow: Error?

    // MARK: - Init

    nonisolated init(
        result: GoogleSignInResult = GoogleSignInResult(
            credential: GoogleAuthProvider.credential(withIDToken: "fake-id-token", accessToken: "fake-access-token"),
            displayName: "Josef Antoni",
            email: "josef@example.com"
        ),
        errorToThrow: Error? = nil
    ) {
        self.result = result
        self.errorToThrow = errorToThrow
    }

    // MARK: - Functions

    func signIn() async throws -> GoogleSignInResult {
        if let errorToThrow { throw errorToThrow }
        return result
    }
}
#endif
