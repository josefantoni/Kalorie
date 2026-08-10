//
//  GoogleSessionProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.08.2026.
//

import GoogleSignIn

protocol GoogleSessionProviderProtocol {
    func clearSession()
}

struct GoogleSessionProvider: GoogleSessionProviderProtocol {

    // MARK: - Functions

    func clearSession() {
        GIDSignIn.sharedInstance.signOut()
    }
}

#if DEBUG
final class GoogleSessionProviderFake: GoogleSessionProviderProtocol {

    // MARK: - Properties

    private(set) var clearSessionCallCount = 0

    // MARK: - Functions

    func clearSession() {
        clearSessionCallCount += 1
    }
}
#endif
