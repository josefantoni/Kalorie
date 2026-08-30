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

protocol AuthProviderProtocol {
    var userId: String? { get }
    var isAnonymous: Bool { get }
    var displayName: String? { get }
    var lastSignInDate: Date? { get }
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
}

#if DEBUG
struct AuthProviderFake: AuthProviderProtocol {

    // MARK: - Properties

    var userId: String? = "test-user-id"
    var isAnonymous = true
    var displayName: String?
    var lastSignInDate: Date? = .now
}
#endif
