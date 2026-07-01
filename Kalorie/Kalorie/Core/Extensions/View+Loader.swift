//
//  View+Loader.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import SwiftUI

extension View {
    func loader(_ isLoading: Bool) -> some View {
        overlay {
            if isLoading {
                ProgressView()
            }
        }
        .allowsHitTesting(!isLoading)
    }
}
