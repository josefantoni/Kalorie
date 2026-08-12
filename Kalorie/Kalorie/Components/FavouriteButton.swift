//
//  FavouriteButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import SwiftUI

struct FavouriteButton: View {

    // MARK: - Properties

    let isFavourite: Bool
    let action: () -> Void

    // MARK: - Body

    var body: some View {
        Button {
            action()
        } label: {
            HStack(spacing: 6) {
                Text(L10n.Common.buttonFavourite)
                BaseImage(imageName: isFavourite ? .heartFill : .heart)
                    .imageScale(.medium)
                    .symbolEffect(.bounce, value: isFavourite)
            }
            .contentTransition(.symbolEffect(.replace))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isFavourite ? Color.white : .red)
        .background {
            Capsule()
                .fill(isFavourite ? Color.red : .clear)
                .strokeBorder(Color.red, lineWidth: 1)
        }
        .contentShape(.capsule)
        .accessibilityAddTraits(isFavourite ? .isSelected : [])
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 20) {
        FavouriteButton(isFavourite: false) {}
        FavouriteButton(isFavourite: true) {}
    }
}
