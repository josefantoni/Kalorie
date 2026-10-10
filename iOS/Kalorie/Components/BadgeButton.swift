//
//  BadgeButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.10.2026.
//

import SwiftUI

struct BadgeButton: View {

    // MARK: - Properties

    let imageName: BaseImageName
    var size: CGFloat = 34
    var accessibilityLabel: String?
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    // MARK: - Body

    var body: some View {
        if let accessibilityLabel {
            button.accessibilityLabel(accessibilityLabel)
        } else {
            button
        }
    }

    // MARK: - Functions

    private var button: some View {
        Button(action: action) {
            Image(systemName: imageName.rawValue)
                .font(.system(size: 18))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
        }
        .buttonStyle(.borderless)
        .background(Color.accentColor)
        .clipShape(.circle)
        .opacity(isEnabled ? 1 : 0.4)
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 12) {
        BadgeButton(imageName: .barCode) {}
        BadgeButton(imageName: .plus) {}
            .disabled(true)
    }
    .padding()
}
