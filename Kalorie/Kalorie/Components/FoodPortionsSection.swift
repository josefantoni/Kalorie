//
//  FoodPortionsSection.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import SwiftUI

struct FoodPortionDraft: Identifiable, Equatable {

    // MARK: - Properties

    let id = UUID()
    var name: String
    var gramsText: String
}

struct FoodPortionsSection: View {

    // MARK: - Properties

    @Binding var portions: [FoodPortionDraft]
    var measure: FoodMeasure = .grams

    @FocusState private var focusedPortionId: UUID?

    // MARK: - Body

    var body: some View {
        Group {
            Section(header: Text(L10n.FoodPortion.sectionTitle)) {
                ForEach(Array(portions.enumerated()), id: \.element.id) { index, draft in
                    PortionInputRow(
                        name: $portions[index].name,
                        gramsText: $portions[index].gramsText,
                        measure: measure,
                        focusedField: $focusedPortionId,
                        focusValue: draft.id
                    )
                        .padding(.bottom, Self.rowSpacing)
                        .overlay(alignment: .bottom) {
                            if index < portions.count - 1 {
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
                                portions.removeAll { $0.id == draft.id }
                            } label: {
                                Image(systemName: "trash")
                            }
                        }
                        .listRowInsets(EdgeInsets(
                            top: index == 0 ? Self.edgeRowSpacing : Self.rowSpacing,
                            leading: 0,
                            bottom: index == portions.count - 1 ? 8 : 0,
                            trailing: 0
                        ))
                }
            }

            Section {
                Button {
                    portions.append(FoodPortionDraft(name: "", gramsText: ""))
                } label: {
                    Image(systemName: BaseImageName.plus.rawValue)
                        .font(.footnote)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .frame(width: Self.addButtonSize, height: Self.addButtonSize)
                }
                .background(Color.accentColor)
                .clipShape(.circle)
                .disabled(!canAddPortion)
                .opacity(canAddPortion ? 1 : 0.4)
                .frame(maxWidth: .infinity)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
            .listSectionSpacing(0)
        }
    }

    fileprivate static let addButtonSize: CGFloat = 28
    private static let rowSpacing: CGFloat = 12
    private static let edgeRowSpacing: CGFloat = 18

    // MARK: - Functions

    private var canAddPortion: Bool {
        portions.allSatisfy { draft in
            !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !draft.gramsText.isEmpty
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodPortionsSection(portions: .constant([FoodPortionDraft(name: "1 balení", gramsText: "33"), FoodPortionDraft(name: "1 balení", gramsText: "33")]))
    }
}
