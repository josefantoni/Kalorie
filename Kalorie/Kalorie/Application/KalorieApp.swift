//
//  KalorieApp.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//
//  swiftlint:disable colon

import SwiftUI
import FirebaseCore


class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        return true
    }
}


@main
struct KalorieApp: App {
    
    // MARK: - Properties
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    
    // MARK: - Body
    
    var body: some Scene {
        WindowGroup {
            DashboardView(
                viewModel: DashboardViewModel(
                    container: PersistentContainer.container
                )
            )
        }
    }
}
