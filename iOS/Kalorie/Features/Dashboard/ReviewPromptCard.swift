//
//  ReviewPromptCard.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.11.2026.
//

import SwiftUI

struct ReviewPromptCard: View {

    // MARK: - Properties

    let onRate: () -> Void
    let onDismiss: () -> Void

    private let cornerRadius: CGFloat = 24
    private let badgeSize: CGFloat = 36

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: BaseImageName.heartFill.rawValue)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: badgeSize, height: badgeSize)
                    .background(Color.accentColor, in: .circle)
                    .accessibilityHidden(true)
                Text(L10n.Dashboard.reviewPromptTitle)
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: onDismiss) {
                    Image(systemName: BaseImageName.close.rawValue)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(L10n.Dashboard.reviewPromptActionNotNow)
            }
            Text(L10n.Dashboard.reviewPromptBody)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onRate) {
                Text(L10n.Dashboard.reviewPromptActionRate)
                    .fontWeight(.bold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.glass(.regular.tint(.accentColor)))
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(16)
        .background(Color.accentSecondary, in: .rect(cornerRadius: cornerRadius))
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    ReviewPromptCard(onRate: {}) {}
        .padding()
}
#endif
