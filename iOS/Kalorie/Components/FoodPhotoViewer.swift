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

    @State private var isShown = false

    // MARK: - Body

    var body: some View {
        ZStack {
            Color.black.opacity(isShown ? 0.5 : 0).ignoresSafeArea()
            Color.clear
                .aspectRatio(1, contentMode: .fit)
                .overlay { FoodPhotoContent(photo: photo, contentMode: .fill) }
                .clipShape(.circle)
                .padding(.horizontal, 16)
                .scaleEffect(isShown ? 1 : 0.6)
                .opacity(isShown ? 1 : 0)
        }
        .presentationBackground(.clear)
        .contentShape(.rect)
        .onTapGesture(perform: close)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(L10n.FoodPhoto.title)
        .onAppear {
            withAnimation(.easeOut(duration: 0.25)) { isShown = true }
        }
    }

    // MARK: - Functions

    private func close() {
        withAnimation(.easeIn(duration: 0.2)) {
            isShown = false
        } completion: {
            withoutAnimation(onClose)
        }
    }
}

func withoutAnimation(_ body: () -> Void) {
    var transaction = Transaction()
    transaction.disablesAnimations = true
    withTransaction(transaction, body)
}
