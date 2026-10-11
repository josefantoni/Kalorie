//
//  FoodPhotoViewer.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import SwiftUI

struct FoodPhotoViewer: View {

    // MARK: - Properties

    let photo: FoodItemFormPhoto
    let onClose: () -> Void

    // MARK: - Body

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            FoodPhotoContent(photo: photo, contentMode: .fit)
        }
        .contentShape(.rect)
        .onTapGesture(perform: onClose)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(L10n.FoodPhoto.title)
    }
}
