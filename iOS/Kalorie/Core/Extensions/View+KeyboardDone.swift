//
//  View+KeyboardDone.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

extension View {
    func keyboardDoneToolbar(isVisible: Bool = true) -> some View {
        modifier(KeyboardDoneBar(isEnabled: isVisible))
    }
}

private struct KeyboardDoneBar: ViewModifier {
    // MARK: - Properties

    let isEnabled: Bool

    @State private var isKeyboardVisible = false

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if isEnabled && isKeyboardVisible {
                    HStack {
                        Spacer()
                        Button {
                            // Resigning inside the glass button's action animates the keyboard's safe-area
                            // change with the button's bouncy press spring: the list frame overshoots and
                            // briefly hides rows at the bottom edge. Deferring it keeps the keyboard curve.
                            DispatchQueue.main.async {
                                UIApplication.shared.sendAction(
                                    #selector(UIResponder.resignFirstResponder),
                                    to: nil,
                                    from: nil,
                                    for: nil
                                )
                            }
                        } label: {
                            Text(L10n.Common.buttonDone)
                                .padding(.all, 4)
                        }
                        .buttonStyle(.glass)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
                isKeyboardVisible = true
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                isKeyboardVisible = false
            }
    }
}
