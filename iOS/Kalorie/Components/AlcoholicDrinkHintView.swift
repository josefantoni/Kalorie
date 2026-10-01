//
//  AlcoholicDrinkHintView.swift
//  Kalorie
//
//  Created by Josef Antoni on 01.10.2026.
//

import SwiftUI

struct AlcoholicDrinkHintView: View {

    // MARK: - Properties

    let formattedAlcoholByVolume: String
    @Binding var isPopoverVisible: Bool

    // MARK: - Body

    var body: some View {
        Button {
            isPopoverVisible = true
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "wineglass")
                Text(L10n.Common.alcoholicDrinkLabel)
                Text(verbatim: "· \(formattedAlcoholByVolume)")
                Image(systemName: "info.circle")
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .buttonStyle(.glass)
        .controlSize(.small)
        .popover(isPresented: $isPopoverVisible) {
            Text(L10n.Common.alcoholicDrinkExplanation)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: 280)
                .padding()
                .presentationCompactAdaptation(.popover)
        }
    }
}

// MARK: - Preview

#Preview {
    AlcoholicDrinkHintView(formattedAlcoholByVolume: "4.4 %", isPopoverVisible: .constant(false))
}
