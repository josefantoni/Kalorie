//
//  View+Loader.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import SwiftUI

struct LoaderIndicator: View {

    // MARK: - Body

    var body: some View {
        AccentProgressView()
            .frame(width: Constants.Loader.size, height: Constants.Loader.size)
            .background(Color(.systemGroupedBackground), in: .rect(cornerRadius: Constants.Loader.cornerRadius))
    }
}

extension View {
    func loader(_ isLoading: Bool) -> some View {
        overlay {
            if isLoading {
                LoaderIndicator()
            }
        }
        .allowsHitTesting(!isLoading)
    }
}
