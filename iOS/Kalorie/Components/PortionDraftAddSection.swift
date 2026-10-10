//
//  PortionDraftAddSection.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

struct PortionDraftAddSection: View {

    // MARK: - Properties

    @Binding var drafts: [FoodPortionDraft]
    var focusedField: FocusState<UUID?>.Binding

    // MARK: - Body

    var body: some View {
        Section {
            BadgeButton(imageName: .plus) {
                let draft = FoodPortionDraft.blank
                drafts.append(draft)
                focusedField.wrappedValue = draft.id
            }
            .disabled(!canAddDraft)
            .frame(maxWidth: .infinity)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .listSectionSpacing(0)
    }

    // MARK: - Functions

    private var canAddDraft: Bool {
        drafts.allSatisfy(\.isComplete)
    }
}
