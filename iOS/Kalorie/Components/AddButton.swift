//
//  AddButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import SwiftUI

struct AddButton: View {

    // MARK: - Properties

    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    // MARK: - Body

    var body: some View {
        Button(action: action) {
            Image(systemName: BaseImageName.plus.rawValue)
                .font(.footnote)
                .fontWeight(.semibold)
                .foregroundStyle(.white)
                .frame(width: Self.size, height: Self.size)
        }
        .buttonStyle(.borderless)
        .background(Color.accentColor)
        .clipShape(.circle)
        .opacity(isEnabled ? 1 : 0.4)
    }

    // MARK: - Functions

    private static var size: CGFloat { 36 }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 12) {
        AddButton {}
        AddButton {}
            .disabled(true)
    }
    .padding()
}
