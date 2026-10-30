//
//  HintCard.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.10.2026.
//

import SwiftUI

struct HintCard: View {

    // MARK: - Properties

    let message: String
    let actionTitle: String
    let onTap: () -> Void
    let onAction: () -> Void

    // MARK: - Body

    var body: some View {
        HStack(spacing: 6) {
            Text(message)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundStyle(.primary)
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)
                .background(Color.accentColor.opacity(0.2), in: messageShape)
            Button(action: onAction) {
                Text(actionTitle)
                    .padding(.horizontal, 16)
                    .frame(maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .fontWeight(.semibold)
            }
            .buttonStyle(.borderless)
            .background(Color.accentColor.opacity(0.2), in: actionShape)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Functions

    private var messageShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: 26, bottomLeadingRadius: 26, bottomTrailingRadius: 0, topTrailingRadius: 0)
    }

    private var actionShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: 26, topTrailingRadius: 26)
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    List {
        Section {
            HintCard(message: "Can't find a food? Add it.", actionTitle: "Hide", onTap: {}, onAction: {})
                .listRowInsets(EdgeInsets())
        }
        .listRowBackground(Color.clear)
    }
}
#endif
