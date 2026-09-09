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

    // MARK: - Body

    var body: some View {
        Section(header: Text(L10n.FoodPortion.sectionTitle)) {
            ForEach($portions) { $draft in
                portionRow($draft)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            portions.removeAll { $0.id == draft.id }
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
            }
            Button(L10n.FoodPortion.buttonAdd) {
                portions.append(FoodPortionDraft(name: "", gramsText: ""))
            }
        }
    }

    // MARK: - Functions

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
            Text(L10n.Common.unitGrams)
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
