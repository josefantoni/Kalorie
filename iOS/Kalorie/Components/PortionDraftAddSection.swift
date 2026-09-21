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
            Button {
                let draft = FoodPortionDraft.blank
                drafts.append(draft)
                focusedField.wrappedValue = draft.id
            } label: {
                Image(systemName: BaseImageName.plus.rawValue)
                    .font(.footnote)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .frame(width: Self.addButtonSize, height: Self.addButtonSize)
            }
            .background(Color.accentColor)
            .clipShape(.circle)
            .disabled(!canAddDraft)
            .opacity(canAddDraft ? 1 : 0.4)
            .frame(maxWidth: .infinity)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .listSectionSpacing(0)
    }

    private static let addButtonSize: CGFloat = 28

    // MARK: - Functions

    private var canAddDraft: Bool {
        drafts.allSatisfy(\.isComplete)
    }
}
