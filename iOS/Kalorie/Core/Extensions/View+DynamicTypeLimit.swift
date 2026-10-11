//
//  View+DynamicTypeLimit.swift
//  Kalorie
//
//  Created by Josef Antoni on 11.10.2026.
//

import SwiftUI
import UIKit

extension View {
    // Sheets and popovers are separate hosting controllers and do not inherit the limit set on the app root.
    func dynamicTypeLimit() -> some View {
        dynamicTypeSize(...DynamicTypeSize.xLarge)
    }
}

// Menus, pickers, alerts, confirmation dialogs, date picker popups and the share sheet are rendered by UIKit,
// ignore dynamicTypeSize and follow the scene's content size category. Keep it equal to the xLarge above.
enum UIKitDynamicTypeLimit {

    // MARK: - Properties

    static let maximum: UIContentSizeCategory = .extraLarge

    // MARK: - Functions

    static func overrideCategory(forSystem system: UIContentSizeCategory) -> UIContentSizeCategory? {
        system > maximum ? maximum : nil
    }

    @MainActor
    static func apply() {
        let override = overrideCategory(forSystem: UIApplication.shared.preferredContentSizeCategory)
        for case let windowScene as UIWindowScene in UIApplication.shared.connectedScenes {
            if let override {
                windowScene.traitOverrides.preferredContentSizeCategory = override
            } else {
                windowScene.traitOverrides.remove(UITraitPreferredContentSizeCategory.self)
            }
        }
    }
}
