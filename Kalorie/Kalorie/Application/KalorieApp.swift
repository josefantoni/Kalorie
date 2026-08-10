//
//  KalorieApp.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import SwiftUI
import FirebaseCore
import GoogleSignIn

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
    @StateObject private var authState = AuthStateObserver(
        resumePendingMerge: MigrateAnonymousDataUseCase(
            dataProvider: FirestoreDataProvider(),
            authProvider: AuthProvider(),
            authCommandProvider: AuthCommandProvider(),
            snapshotStore: PendingMergeSnapshotStore()
        )
    )

    // MARK: - Body

    var body: some Scene {
        WindowGroup {
            switch authState.state {
            case .idle, .loading:
                ProgressView()
            case .loaded:
                DashboardConfigurator().createView(mergeStatusReporting: authState)
                    .id(authState.userId)
                    .overlay {
                        if authState.isMerging {
                            mergingOverlay
                        }
                    }
                    .allowsHitTesting(!authState.isMerging)
                    .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
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

    // MARK: - Functions

    private var mergingOverlay: some View {
        ZStack {
            Color.black.opacity(0.15).ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView()
                Text(L10n.Auth.mergingMessage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            .padding(24)
            .background(.regularMaterial, in: .rect(cornerRadius: 16))
            .padding(.horizontal, 40)
        }
    }
}
