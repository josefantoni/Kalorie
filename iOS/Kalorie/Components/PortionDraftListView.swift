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
    var firstRowTopInset: CGFloat = 12
    var focusedField: FocusState<UUID?>.Binding
    var onDelete: (FoodPortionDraft) -> Void
    
    private static let rowSpacing: CGFloat = 10
    private static let rowTopInset: CGFloat = 4

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
                            .padding(.horizontal, 12)
                    }
                }
                // .hidden alone doesn't reliably suppress this List's row separator; the clear tint forces it.
                .listRowSeparator(.hidden)
                .listRowSeparatorTint(.clear)
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        onDelete(draft)
                    } label: {
                        Image(systemName: BaseImageName.trash.rawValue)
                    }
                }
                .listRowInsets(EdgeInsets(
                    top: index == 0 ? firstRowTopInset : Self.rowTopInset,
                    leading: 0,
                    bottom: index == drafts.count - 1 ? 8 : 0,
                    trailing: 0
                ))
        }
    }
}

// MARK: - Preview

private struct PortionDraftListPreview: View {

    @State private var drafts = [
        FoodPortionDraft(name: "1 balení", gramsText: "33"),
        FoodPortionDraft(name: "1 lžíce", gramsText: "15"),
        FoodPortionDraft.blank
    ]
    @FocusState private var focusedField: UUID?

    var body: some View {
        List {
            PortionDraftListView(drafts: $drafts, focusedField: $focusedField) { draft in
                drafts.removeAll { $0.id == draft.id }
            }
        }
    }
}

#Preview {
    PortionDraftListPreview()
}
