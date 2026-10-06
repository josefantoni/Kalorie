//
//  PrimaryButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import SwiftUI

struct PrimaryButton: View {

    enum Style {
        case primary
        case destructive
    }

    // MARK: - Properties

    let title: String
    let style: Style
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    // MARK: - Init

    init(_ title: String, style: Style = .primary, action: @escaping () -> Void) {
        self.title = title
        self.style = style
        self.action = action
    }

    // MARK: - Body

    var body: some View {
        Button(role: style == .destructive ? .destructive : nil, action: action) {
            Text(title)
                .font(.body)
                .fontWeight(.bold)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: Self.height)
                .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(tint).interactive(), in: .capsule)
        .opacity(isEnabled ? 1 : 0.4)
    }

    // MARK: - Functions

    private var tint: Color {
        switch style {
        case .primary: .accentColor
        case .destructive: .error
        }
    }

    private static var height: CGFloat { 56 }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 12) {
        PrimaryButton("Export") {}
        PrimaryButton("Delete meal", style: .destructive) {}
        PrimaryButton("Export") {}
            .disabled(true)
    }
    .padding()
}
