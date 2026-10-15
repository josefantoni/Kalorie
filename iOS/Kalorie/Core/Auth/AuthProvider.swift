//
//  AuthProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation
import FirebaseAuth

enum AuthError: Error {
    case notAuthenticated
}

enum AuthProviderKind {
    case apple
    case google
}

protocol AuthProviderProtocol {
    var userId: String? { get }
    var isAnonymous: Bool { get }
    var displayName: String? { get }
    var lastSignInDate: Date? { get }
    var linkedProviderKinds: Set<AuthProviderKind> { get }
    func currentSessionProviderKind() async -> AuthProviderKind?
}

struct AuthProvider: AuthProviderProtocol {

    // MARK: - Properties

    var userId: String? {
        Auth.auth().currentUser?.uid
    }

    var isAnonymous: Bool {
        Auth.auth().currentUser?.isAnonymous ?? true
    }

    var displayName: String? {
        Auth.auth().currentUser?.displayName
    }

    var lastSignInDate: Date? {
        Auth.auth().currentUser?.metadata.lastSignInDate
    }

    var linkedProviderKinds: Set<AuthProviderKind> {
        Set((Auth.auth().currentUser?.providerData ?? []).compactMap { Self.kind(forProviderId: $0.providerID) })
    }

    func currentSessionProviderKind() async -> AuthProviderKind? {
        guard let user = Auth.auth().currentUser else { return nil }
        let signInProvider = try? await user.getIDTokenResult().signInProvider
        return signInProvider.flatMap(Self.kind(forProviderId:))
    }

    private static func kind(forProviderId providerId: String) -> AuthProviderKind? {
        switch providerId {
        case "apple.com": .apple
        case "google.com": .google
        default: nil
        }
    }
}

#if DEBUG
struct AuthProviderFake: AuthProviderProtocol {

    // MARK: - Properties

    var userId: String? = "test-user-id"
    var isAnonymous = true
    var displayName: String?
    var lastSignInDate: Date? = .now
    var linkedProviderKinds: Set<AuthProviderKind> = []
    var sessionProviderKind: AuthProviderKind?

    // MARK: - Functions

    func currentSessionProviderKind() async -> AuthProviderKind? {
        sessionProviderKind
    }
}
#endif
