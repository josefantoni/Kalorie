//
//  AppAttestProviderFactory.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import DeviceCheck
import FirebaseAppCheck
import FirebaseCore

final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {

    // MARK: - Functions

    func createProvider(with app: FirebaseApp) -> (any AppCheckProvider)? {
        // Without App Attest support the device would get no token and App Check enforcement would lock it out.
        if !DCAppAttestService.shared.isSupported {
            return DeviceCheckProvider(app: app)
        }
        return AppAttestProvider(app: app)
    }
}
