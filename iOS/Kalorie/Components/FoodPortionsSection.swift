//
//  FoodPortionsSection.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import SwiftUI

struct FoodPortionsSection: View {

    // MARK: - Properties

    @Binding var portions: [FoodPortionDraft]
    var measure: FoodMeasure = .grams

    @FocusState private var focusedPortionId: UUID?

    // MARK: - Body

    var body: some View {
        Group {
            Section(header: Text(L10n.FoodPortion.sectionTitle)) {
                PortionDraftListView(
                    drafts: $portions,
                    measure: measure,
                    focusedField: $focusedPortionId
                ) { draft in
                    portions.removeAll { $0.id == draft.id }
                }
            }

            PortionDraftAddSection(drafts: $portions, focusedField: $focusedPortionId)
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodPortionsSection(portions: .constant([FoodPortionDraft(name: "1 balení", gramsText: "33"), FoodPortionDraft(name: "1 balení", gramsText: "33")]))
    }
}
