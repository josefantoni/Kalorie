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
    var onNutritionLabelScanTapped: (() -> Void)?
    var onFieldEdited: (FoodItemFormField) -> Void = { _ in }

    // MARK: - Body

    var body: some View {
        Group {
            FoodPortionsSection(portions: $formInput.portions, measure: formInput.measure)
            Section {
                FloatingLabelTextField(
                    title: L10n.AddFood.fieldNameTitle,
                    text: nameBinding,
                    placeholder: L10n.AddFood.fieldNamePlaceholder,
                    isHighlighted: highlightedFields.contains(.name)
                )
                .listRowInsets(.vertical, 0)
                barcodeRowView
                    .listRowInsets(.vertical, 0)
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
            FloatingLabelTextField(
                title: L10n.AddFood.fieldBarcodeTitle,
                text: .constant(formInput.scannedCode),
                message: formInput.scannedCode.isEmpty ? .hint(L10n.AddFood.fieldBarcodeMissingLabel) : nil,
                keyboardType: .numberPad
            )
            .disabled(true)
        case .editable(let onScanTapped):
            HStack {
                FloatingLabelTextField(
                    title: L10n.AddFood.fieldBarcodeTitle,
                    text: $formInput.scannedCode,
                    message: formInput.scannedCode.isEmpty ? .warning(L10n.AddFood.warningMissingBarcode) : nil,
                    keyboardType: .numberPad
                )
                BaseButton(style: .plain, imageName: .barCode, imageSize: .medium) {
                    onScanTapped()
                }
                .accessibilityLabel(L10n.AddFood.nutritionLabelBarcodeScanAccessibility)
            }
        }
    }

    @ViewBuilder private var nutritionLabelScanButton: some View {
        if let onNutritionLabelScanTapped {
            Button {
                onNutritionLabelScanTapped()
            } label: {
                Label(L10n.AddFood.buttonScanNutritionLabel, systemImage: BaseImageName.camera.rawValue)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemFormSections(formInput: .constant(FoodItemFormInput()), barcodeRow: .editable {})
    }
}
