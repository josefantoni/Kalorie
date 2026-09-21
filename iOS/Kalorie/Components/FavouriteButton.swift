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

    @State private var isLabelVisible = false
    @State private var wasTapped = false
    @State private var labelRequest = 0

    private var showsLabel: Bool { isFavourite && isLabelVisible }

    // MARK: - Body

    var body: some View {
        Button {
            wasTapped = true
            action()
        } label: {
            HStack(spacing: 6) {
                if showsLabel {
                    Text(L10n.Common.buttonFavourite)
                        .transition(.opacity)
                }
                BaseImage(imageName: isFavourite ? .heartFill : .heart)
                    .imageScale(.medium)
                    .symbolEffect(.bounce, value: isFavourite)
            }
            .contentTransition(.symbolEffect(.replace))
            .padding(.horizontal, showsLabel ? 16 : 10)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(showsLabel ? Color.white : .red)
        .background {
            Capsule()
                .fill(showsLabel ? Color.red : .clear)
        }
        .contentShape(.capsule)
        .animation(.snappy, value: showsLabel)
        .onChange(of: isFavourite) { _, newValue in
            isLabelVisible = newValue && wasTapped
            wasTapped = false
            labelRequest += 1
        }
        .task(id: labelRequest) {
            guard isLabelVisible else { return }
            do {
                try await Task.sleep(for: .seconds(1))
            } catch {
                return
            }
            isLabelVisible = false
        }
        .accessibilityLabel(L10n.Common.buttonFavourite)
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
