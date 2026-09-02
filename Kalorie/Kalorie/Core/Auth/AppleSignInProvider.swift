//
//  AppleSignInProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 26.08.2026.
//

import AuthenticationServices
import FirebaseAuth
import Foundation
import UIKit

enum AppleSignInError: Error {
    case invalidCredential
    case signInAlreadyInProgress
    case presentationAnchorUnavailable
}

struct AppleSignInResult {
    let credential: AuthCredential
    let fullName: PersonNameComponents?
    let email: String?
}

@MainActor
protocol AppleSignInProviderProtocol {
    func signIn() async throws -> AppleSignInResult
}

final class AppleSignInProvider: NSObject, AppleSignInProviderProtocol {

    // MARK: - Properties

    private let lock = NSLock()
    private var continuation: CheckedContinuation<AppleSignInResult, Error>?
    private var pendingNonce: String?

    // MARK: - Init

    nonisolated override init() {
        super.init()
    }

    // MARK: - Functions

    func signIn() async throws -> AppleSignInResult {
        try lock.withLock {
            guard continuation == nil else {
                throw AppleSignInError.signInAlreadyInProgress
            }
        }

        return try await withCheckedThrowingContinuation { continuation in
            let rawNonce = NonceGenerator.randomNonceString()
            lock.withLock {
                self.continuation = continuation
                self.pendingNonce = rawNonce
            }

            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            request.nonce = NonceGenerator.sha256(rawNonce)

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    private func takeContinuation() -> (continuation: CheckedContinuation<AppleSignInResult, Error>, nonce: String?)? {
        lock.withLock {
            guard let continuation else { return nil }
            let nonce = pendingNonce
            self.continuation = nil
            self.pendingNonce = nil
            return (continuation, nonce)
        }
    }
}

extension AppleSignInProvider: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let (continuation, nonce) = takeContinuation() else { return }

        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let identityToken = credential.identityToken,
            let idTokenString = String(data: identityToken, encoding: .utf8),
            let rawNonce = nonce
        else {
            continuation.resume(throwing: AppleSignInError.invalidCredential)
            return
        }

        continuation.resume(returning: AppleSignInResult(
            credential: OAuthProvider.appleCredential(
                withIDToken: idTokenString,
                rawNonce: rawNonce,
                fullName: credential.fullName
            ),
            fullName: credential.fullName,
            email: credential.email
        ))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        guard let (continuation, _) = takeContinuation() else { return }
        continuation.resume(throwing: error)
    }
}

extension AppleSignInProvider: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard
            let window = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive })?
                .keyWindow
        else {
            if let (continuation, _) = takeContinuation() {
                continuation.resume(throwing: AppleSignInError.presentationAnchorUnavailable)
            }
            return UIWindow()
        }
        return window
    }
}

#if DEBUG
struct AppleSignInProviderFake: AppleSignInProviderProtocol {

    // MARK: - Properties

    var result: AppleSignInResult
    var errorToThrow: Error?

    // MARK: - Init

    nonisolated init(
        result: AppleSignInResult = AppleSignInResult(
            credential: OAuthProvider.appleCredential(withIDToken: "fake-identity-token", rawNonce: "fake-nonce", fullName: nil),
            fullName: nil,
            email: nil
        ),
        errorToThrow: Error? = nil
    ) {
        self.result = result
        self.errorToThrow = errorToThrow
    }

    // MARK: - Functions

    func signIn() async throws -> AppleSignInResult {
        if let errorToThrow { throw errorToThrow }
        return result
    }
}
#endif
