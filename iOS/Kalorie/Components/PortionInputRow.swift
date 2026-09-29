//
//  PortionInputRow.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

struct PortionInputRow<FocusValue: Hashable>: View {

    // MARK: - Properties

    @Binding var name: String
    @Binding var gramsText: String
    var measure: FoodMeasure
    var focusedField: FocusState<FocusValue?>.Binding
    var focusValue: FocusValue

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                FloatingLabelTextField(
                    title: L10n.FoodPortion.fieldNameTitle,
                    text: $name,
                    focus: focusedField,
                    equals: focusValue
                )
                .frame(maxWidth: .infinity)
                TextField("0", text: $gramsText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 50)
                    .onChange(of: gramsText) { _, text in
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
                            gramsText = sanitized
                        }
                    }
                Text(measure.unitSymbol)
                    .foregroundStyle(.secondary)
            }

            quickAddRow
        }
        .padding(.horizontal)
    }

    // MARK: - Functions

    private var quickAddRow: some View {
        HStack(spacing: Self.quickAddSpacing) {
            ForEach(Self.quickAddOptions, id: \.self) { option in
                Button {
                    name = option
                    focusedField.wrappedValue = nil
                } label: {
                    Text(option)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: Self.quickAddHeight)
                        .background(Color.accentColor)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private static var quickAddHeight: CGFloat { 20 }
    private static var quickAddSpacing: CGFloat { 8 }
    private static var quickAddOptions: [String] {
        [L10n.FoodPortion.quickAddPiece, L10n.FoodPortion.quickAddPackage, L10n.FoodPortion.quickAddSpoon]
    }
}

// MARK: - Preview

private struct PortionInputRowPreview: View {

    @State private var name = "1 balení"
    @State private var gramsText = "33"
    @FocusState private var focusedField: Bool?

    var body: some View {
        List {
            PortionInputRow(
                name: $name,
                gramsText: $gramsText,
                measure: .grams,
                focusedField: $focusedField,
                focusValue: true
            )
            .listRowInsets(EdgeInsets())
        }
    }
}

#Preview {
    PortionInputRowPreview()
}
