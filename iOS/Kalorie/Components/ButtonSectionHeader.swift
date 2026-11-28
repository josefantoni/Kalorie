//
//  ButtonSectionHeader.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.11.2026.
//

import SwiftUI

struct ButtonSectionHeader<Accessory: View>: View {

    // MARK: - Properties

    let title: String
    @ViewBuilder let accessory: Accessory

    // MARK: - Body

    var body: some View {
        // The button is taller than the title; as an overlay it cannot change the header's height and spacing.
        Text(title)
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay(alignment: .trailing) {
                accessory.fixedSize()
            }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    List {
        Section(header: ButtonSectionHeader(title: "With button") { BadgeButton(imageName: .plus, size: 28) {} }) {
            Text(verbatim: "Row")
        }
        Section(header: Text(verbatim: "Plain")) {
            Text(verbatim: "Row")
        }
    }
    .appSectionSpacing()
}
#endif
