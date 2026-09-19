//
//  SaveToolbarButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

struct SaveToolbarButton: View {

    // MARK: - Properties

    let title: String
    let showCheckmark: Bool
    let isEnabled: Bool
    let action: () -> Void

    // MARK: - Body

    var body: some View {
        Button {
            action()
        } label: {
            if showCheckmark {
                Image(systemName: "checkmark")
                    .foregroundStyle(.green)
                    .transition(.scale.combined(with: .opacity))
            } else {
                Text(title)
                    .transition(.opacity)
            }
        }
        .animation(.spring(duration: 0.4), value: showCheckmark)
        .disabled(!isEnabled)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        Text("Preview")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    SaveToolbarButton(title: "Save", showCheckmark: false, isEnabled: true) {}
                }
            }
    }
}
