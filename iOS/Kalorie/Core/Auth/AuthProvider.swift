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
    var providerDisplayName: String? { get }
    var linkedProviderKinds: Set<AuthProviderKind> { get }
    func currentSessionProviderKind() async -> AuthProviderKind?
    func currentAuthenticationDate() async -> Date?
    func maintainerClaim(forcingRefresh: Bool) async throws -> Bool
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

    var providerDisplayName: String? {
        Auth.auth().currentUser?.providerData.compactMap(\.displayName).first { !$0.isEmpty }
    }

    var linkedProviderKinds: Set<AuthProviderKind> {
        Set((Auth.auth().currentUser?.providerData ?? []).compactMap { Self.kind(forProviderId: $0.providerID) })
    }

    func currentSessionProviderKind() async -> AuthProviderKind? {
        guard let user = Auth.auth().currentUser else { return nil }
        let signInProvider = try? await user.getIDTokenResult().signInProvider
        return signInProvider.flatMap(Self.kind(forProviderId:))
    }

    func currentAuthenticationDate() async -> Date? {
        guard let user = Auth.auth().currentUser else { return nil }
        return try? await user.getIDTokenResult(forcingRefresh: true).authDate
    }

    func maintainerClaim(forcingRefresh: Bool) async throws -> Bool {
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        let result = try await user.getIDTokenResult(forcingRefresh: forcingRefresh)
        return result.claims[Constants.Auth.maintainerClaim] as? Bool ?? false
    }

    private static func kind(forProviderId providerId: String) -> AuthProviderKind? {
        switch providerId {
        case Constants.Auth.appleProviderId: .apple
        case Constants.Auth.googleProviderId: .google
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
    var providerDisplayName: String?
    var linkedProviderKinds: Set<AuthProviderKind> = []
    var sessionProviderKind: AuthProviderKind?
    var authenticationDate: Date? = .now
    var isMaintainer = false
    let maintainerClaimRequests = MaintainerClaimRequestLog()

    // MARK: - Functions

    func currentSessionProviderKind() async -> AuthProviderKind? {
        sessionProviderKind
    }

    func currentAuthenticationDate() async -> Date? {
        authenticationDate
    }

    func maintainerClaim(forcingRefresh: Bool) async throws -> Bool {
        maintainerClaimRequests.forcingRefreshValues.append(forcingRefresh)
        return isMaintainer
    }
}

final class MaintainerClaimRequestLog {
    var forcingRefreshValues: [Bool] = []
}
#endif
