//
//  PortionDraftListView.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

struct FoodPortionDraft: Identifiable, Equatable {

    // MARK: - Properties

    let id = UUID()
    var name: String
    var gramsText: String

    // MARK: - Functions

    static var blank: FoodPortionDraft {
        FoodPortionDraft(name: "", gramsText: "")
    }

    var isComplete: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !gramsText.isEmpty
    }
}

struct PortionDraftListView: View {

    // MARK: - Properties

    @Binding var drafts: [FoodPortionDraft]
    var measure: FoodMeasure = .grams
    var firstRowTopInset: CGFloat = 18
    var focusedField: FocusState<UUID?>.Binding
    var onDelete: (FoodPortionDraft) -> Void

    // MARK: - Body

    var body: some View {
        ForEach(Array(drafts.enumerated()), id: \.element.id) { index, draft in
            PortionInputRow(
                name: $drafts[index].name,
                gramsText: $drafts[index].gramsText,
                measure: measure,
                focusedField: focusedField,
                focusValue: draft.id
            )
                .padding(.bottom, Self.rowSpacing)
                .overlay(alignment: .bottom) {
                    if index < drafts.count - 1 {
                        Rectangle()
                            .fill(Color(uiColor: .separator))
                            .frame(height: 1)
                            .padding(.horizontal, 16)
                    }
                }
                // .hidden alone doesn't reliably suppress this List's row separator; the clear tint forces it.
                .listRowSeparator(.hidden)
                .listRowSeparatorTint(.clear)
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        onDelete(draft)
                    } label: {
                        Image(systemName: "trash")
                    }
                }
                .listRowInsets(EdgeInsets(
                    top: index == 0 ? firstRowTopInset : Self.rowSpacing,
                    leading: 0,
                    bottom: index == drafts.count - 1 ? 8 : 0,
                    trailing: 0
                ))
        }
    }

    private static let rowSpacing: CGFloat = 12
}
