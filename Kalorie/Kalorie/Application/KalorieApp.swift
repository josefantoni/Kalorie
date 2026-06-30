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
    @State private var isStoreLoaded = false

    // MARK: - Body

    var body: some Scene {
        WindowGroup {
            if isStoreLoaded {
                DashboardConfigurator().createView()
            } else {
                Color.clear
                    .task {
                        await PersistentContainer.load()
                        isStoreLoaded = true
                    }
            }
        }
    }
}
