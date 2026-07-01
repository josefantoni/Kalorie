//
//  AuthStateObserver.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation
import FirebaseAuth

final class AuthStateObserver: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading

    // MARK: - Init

    init() {
        Auth.auth().addStateDidChangeListener { [weak self] _, user in
            if user != nil {
                DispatchQueue.main.async { self?.state = .loaded }
            } else {
                self?.signIn()
            }
        }
    }

    // MARK: - Functions

    func retry() {
        state = .loading
        signIn()
    }

    private func signIn() {
        Auth.auth().signInAnonymously { [weak self] _, error in
            DispatchQueue.main.async {
                self?.state = error == nil ? .loaded : .error(error)
            }
        }
    }
}
