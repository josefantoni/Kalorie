//
//  View+BottomPinnedButtons.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.11.2026.
//

import SwiftUI

extension View {
    func bottomPinnedButtons<Buttons: View>(
        isVisible: Bool = true,
        @ViewBuilder buttons: () -> Buttons
    ) -> some View {
        modifier(BottomPinnedButtons(isVisible: isVisible, buttons: buttons()))
    }
}

private struct BottomPinnedButtons<Buttons: View>: ViewModifier {

    // MARK: - Properties

    private static var restingInset: CGFloat { 28 }
    private static var keyboardPadding: CGFloat { 12 }

    let isVisible: Bool
    let buttons: Buttons

    @State private var isKeyboardVisible = false

    private var bottomPadding: CGFloat {
        isKeyboardVisible ? Self.keyboardPadding : Self.restingInset - Self.homeIndicatorInset
    }

    private static var homeIndicatorInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets.bottom ?? 0
    }

    // MARK: - Body

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if isVisible {
                    buttons
                        .padding(.horizontal, 16)
                        .padding(.bottom, bottomPadding)
                        .frame(maxWidth: .infinity)
                        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(.container, edges: .bottom))
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
