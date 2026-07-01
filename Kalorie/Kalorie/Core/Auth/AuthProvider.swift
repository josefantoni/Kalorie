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
}

struct AuthProvider: AuthProviderProtocol {

    // MARK: - Properties

    var userId: String? {
        Auth.auth().currentUser?.uid
    }
}

struct AuthProviderFake: AuthProviderProtocol {

    // MARK: - Properties

    var userId: String? = "test-user-id"
}
