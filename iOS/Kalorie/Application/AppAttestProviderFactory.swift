//
//  AppAttestProviderFactory.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import FirebaseAppCheck
import FirebaseCore

final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {

    // MARK: - Functions

    func createProvider(with app: FirebaseApp) -> (any AppCheckProvider)? {
        AppAttestProvider(app: app)
    }
}
