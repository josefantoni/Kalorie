//
//  KalorieApp.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import SwiftUI
import FirebaseCore

class AppDelegate: NSObject, UIApplicationDelegate {

    // MARK: - Functions

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        return true
    }
}

@main
struct KalorieApp: App {

    // MARK: - Properties

    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var authState = AuthStateObserver()

    // MARK: - Body

    var body: some Scene {
        WindowGroup {
            switch authState.state {
            case .loading:
                ProgressView()
            case .loaded:
                DashboardConfigurator().createView()
            case .error(let error):
                VStack(spacing: 16) {
                    Text(L10n.Auth.errorSignInFailed)
                        .font(.headline)
                    Text(error?.localizedDescription ?? L10n.Common.errorUnknown)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    Button(L10n.Auth.buttonRetry) { authState.retry() }
                }
            }
        }
    }
}
