//
//  FoodItemFormSections.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import SwiftUI

enum FoodItemFormBarcodeRow {
    case hidden
    case locked
    case editable(onScanTapped: () -> Void)
}

struct FoodItemFormSections: View {

    // MARK: - Properties

    @Binding var formInput: FoodItemFormInput
    var highlightedFields: Set<FoodItemFormField> = []
    var barcodeRow: FoodItemFormBarcodeRow = .hidden
    var onNutritionLabelScanTapped: () -> Void = {}
    var onFieldEdited: (FoodItemFormField) -> Void = { _ in }

    // MARK: - Body

    var body: some View {
        Group {
            FoodPortionsSection(portions: $formInput.portions, measure: formInput.measure)
            Section {
                BaseStringTextField(
                    placeholder: L10n.AddFood.fieldNamePlaceholder,
                    title: L10n.AddFood.fieldNameTitle,
                    text: nameBinding,
                    isHighlighted: highlightedFields.contains(.name)
                )
                barcodeRowView
                nutritionLabelScanButton
                FoodItemFormFields(formInput: $formInput, highlightedFields: highlightedFields, onFieldEdited: onFieldEdited)
            }
        }
    }

    // MARK: - Functions

    private var nameBinding: Binding<String> {
        Binding(
            get: { formInput.name },
            set: { formInput.name = $0; onFieldEdited(.name) }
        )
    }

    @ViewBuilder private var barcodeRowView: some View {
        switch barcodeRow {
        case .hidden:
            EmptyView()
        case .locked:
            BaseStringTextField(
                placeholder: formInput.scannedCode.isEmpty ? L10n.AddFood.fieldBarcodeMissingLabel : L10n.AddFood.fieldBarcodePlaceholder,
                title: L10n.AddFood.fieldBarcodeTitle,
                text: .constant(formInput.scannedCode),
                keyboardType: .numberPad
            )
            .disabled(true)
        case .editable(let onScanTapped):
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    BaseStringTextField(
                        placeholder: L10n.AddFood.fieldBarcodePlaceholder,
                        title: L10n.AddFood.fieldBarcodeTitle,
                        text: $formInput.scannedCode,
                        keyboardType: .numberPad
                    )
                    BaseButton(style: .plain, imageName: .barCode, imageSize: .medium) {
                        onScanTapped()
                    }
                    .accessibilityLabel(L10n.AddFood.nutritionLabelBarcodeScanAccessibility)
                }
                if formInput.scannedCode.isEmpty {
                    Text(L10n.AddFood.warningMissingBarcode)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var nutritionLabelScanButton: some View {
        Button {
            onNutritionLabelScanTapped()
        } label: {
            Label(L10n.AddFood.buttonScanNutritionLabel, systemImage: BaseImageName.camera.rawValue)
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemFormSections(formInput: .constant(FoodItemFormInput()), barcodeRow: .editable {})
    }
}
