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
        // App Attest does not issue tokens to an iPhone app running on a Mac, so App Check enforcement would lock it out.
        if
            ProcessInfo.processInfo.isiOSAppOnMac
            || !DCAppAttestService.shared.isSupported
        {
            return DeviceCheckProvider(app: app)
        }
        return AppAttestProvider(app: app)
    }
}
