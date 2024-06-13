//
//  KalorieApp.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import SwiftUI


@main
struct KalorieApp: App {
    
    // MARK: - Body
    
    var body: some Scene {
        WindowGroup {
            DashboardView(viewModel: .live)
        }
    }
}
