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
    var linkedProviderKind: AuthProviderKind? { get }
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

    var linkedProviderKind: AuthProviderKind? {
        // .first relies on an account never having more than one linked provider —
        // enforced by LinkOrMergeCredentialUseCase, not by Firebase Auth itself.
        switch Auth.auth().currentUser?.providerData.first?.providerID {
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
    var linkedProviderKind: AuthProviderKind?
}
#endif
