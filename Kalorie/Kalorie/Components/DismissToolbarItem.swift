//
//  DismissToolbarItem.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import SwiftUI

struct DismissToolbarItem: ToolbarContent {

    // MARK: - Properties

    @Environment(\.dismiss) private var dismiss
    var placement: ToolbarItemPlacement = .topBarLeading

    // MARK: - Body

    var body: some ToolbarContent {
        ToolbarItem(placement: placement) {
            BaseButton(style: .plain, imageName: .close, imageSize: .basic) {
                dismiss()
            }
        }
    }
}
