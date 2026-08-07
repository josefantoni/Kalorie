//
//  AuthCommandProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation
import FirebaseAuth

protocol AuthCommandProviderProtocol {
    func link(with credential: AuthCredential) async throws
    func signIn(with credential: AuthCredential) async throws
    func signOut() throws
    func updateDisplayName(_ name: String) async throws
    func deleteCurrentUser() async throws
}

struct AuthCommandProvider: AuthCommandProviderProtocol {

    // MARK: - Functions

    func link(with credential: AuthCredential) async throws {
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        _ = try await user.link(with: credential)
    }

    func signIn(with credential: AuthCredential) async throws {
        _ = try await Auth.auth().signIn(with: credential)
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }

    func updateDisplayName(_ name: String) async throws {
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        let request = user.createProfileChangeRequest()
        request.displayName = name
        try await request.commitChanges()
    }

    func deleteCurrentUser() async throws {
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        try await user.delete()
    }
}

#if DEBUG
final class AuthCommandProviderFake: AuthCommandProviderProtocol {

    // MARK: - Properties

    var linkError: Error?
    var signInError: Error?
    var signOutError: Error?
    var deleteError: Error?
    private(set) var linkCallCount = 0
    private(set) var signInCallCount = 0
    private(set) var signOutCallCount = 0
    private(set) var updateDisplayNameCallCount = 0
    private(set) var deleteCallCount = 0

    // MARK: - Functions

    func link(with credential: AuthCredential) async throws {
        linkCallCount += 1
        if let linkError { throw linkError }
    }

    func signIn(with credential: AuthCredential) async throws {
        signInCallCount += 1
        if let signInError { throw signInError }
    }

    func signOut() throws {
        signOutCallCount += 1
        if let signOutError { throw signOutError }
    }

    func updateDisplayName(_ name: String) async throws {
        updateDisplayNameCallCount += 1
    }

    func deleteCurrentUser() async throws {
        deleteCallCount += 1
        if let deleteError { throw deleteError }
    }
}
#endif
