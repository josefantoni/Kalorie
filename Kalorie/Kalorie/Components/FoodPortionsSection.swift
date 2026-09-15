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

    // MARK: - Body

    var body: some View {
        Group {
            Section(header: Text(L10n.FoodPortion.sectionTitle)) {
                ForEach($portions) { $draft in
                    portionRow($draft)
                        .listRowSeparator(.hidden)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                portions.removeAll { $0.id == draft.id }
                            } label: {
                                Image(systemName: "trash")
                            }
                        }
                    if draft.id != portions.last?.id {
                        Divider()
                            .listRowInsets(EdgeInsets())
                            .listRowSeparator(.hidden)
                    }
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

    private static let addButtonSize: CGFloat = 28

    // MARK: - Functions

    private var canAddPortion: Bool {
        portions.allSatisfy { draft in
            !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !draft.gramsText.isEmpty
        }
    }

    private func portionRow(_ draft: Binding<FoodPortionDraft>) -> some View {
        HStack {
            BaseStringTextField(
                placeholder: L10n.FoodPortion.fieldNamePlaceholder,
                title: "",
                text: draft.name,
                textAlignment: .leading
            )
            TextField("0", text: draft.gramsText)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 50)
                .onChange(of: draft.gramsText.wrappedValue) { _, text in
                    var seenSeparator = false
                    let sanitized = String(text.filter { char in
                        if char == "." || char == "," {
                            if seenSeparator { return false }
                            seenSeparator = true
                            return true
                        }
                        return char.isASCII && char.isNumber
                    })
                    if sanitized != text {
                        draft.gramsText.wrappedValue = sanitized
                    }
                }
            Text(measure.unitSymbol)
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodPortionsSection(portions: .constant([FoodPortionDraft(name: "1 balení", gramsText: "33")]))
    }
}
