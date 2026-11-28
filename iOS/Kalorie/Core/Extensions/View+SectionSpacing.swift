//
//  View+SectionSpacing.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.11.2026.
//

import SwiftUI

extension View {
    func appSectionSpacing() -> some View {
        listSectionSpacing(.custom(20))
            .contentMargins(.top, Constants.Layout.topContentSpacing, for: .scrollContent)
    }

    // The system header's top inset would add to the top margin; it must be the header's last modifier.
    func appFirstSectionHeader(_ isFirst: Bool = true) -> some View {
        listRowInsets(.top, isFirst ? 0 : nil)
    }

    func hintCardSectionSpacing() -> some View {
        listSectionSpacing(.custom(Constants.Layout.hintCardSpacing))
    }
}
