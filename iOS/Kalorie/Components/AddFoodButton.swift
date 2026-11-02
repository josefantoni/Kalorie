//
//  AddFoodButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 02.11.2026.
//

import SwiftUI

struct AddFoodButton: View {

    // MARK: - Properties

    let isExpanded: Bool
    let isAnimationPlaying: Bool
    let action: () -> Void

    static let expandedSize: CGFloat = 140
    static let collapsedSize: CGFloat = 64

    // MARK: - Body

    var body: some View {
        Button(action: action) {
            ZStack {
                Image(systemName: BaseImageName.plus.rawValue)
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .opacity(isAnimationPlaying ? 0 : 1)
                if isAnimationPlaying {
                    PancakeSyrupAnimation()
                        .padding(10)
                }
            }
            .frame(
                width: isExpanded ? Self.expandedSize : Self.collapsedSize,
                height: isExpanded ? Self.expandedSize : Self.collapsedSize
            )
            .clipShape(Circle())
        }
        .accessibilityLabel(L10n.Dashboard.emptyAddFood)
        .glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)
    }
}
