//
//  FoodItemFormSections.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import SwiftUI

enum FoodItemFormNameFields: Equatable {
    case single
    case allLanguages(originalLanguage: String?)
}

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
    var showsAlcoholByVolumeField = false
    var nameFields: FoodItemFormNameFields = .single
    var onNutritionLabelScanTapped: (() -> Void)?
    var onFieldEdited: (FoodItemFormField) -> Void = { _ in }

    // MARK: - Body

    var body: some View {
        Group {
            FoodPortionsSection(portions: $formInput.portions, measure: formInput.measure)
            
            Section {
                switch nameFields {
                case .single:
                    FloatingLabelTextField(
                        title: L10n.AddFood.fieldNameTitle,
                        text: nameBinding,
                        placeholder: L10n.AddFood.fieldNamePlaceholder,
                        isHighlighted: highlightedFields.contains(.name)
                    )
                    .listRowInsets(.vertical, 0)
                case .allLanguages(let originalLanguage):
                    languageNameField(language: "cs", title: L10n.AddFood.fieldCzechNameTitle, text: $formInput.czName, originalLanguage: originalLanguage, scanTargetLanguage: originalLanguage ?? formInput.nameLanguage)
                    languageNameField(language: "sk", title: L10n.AddFood.fieldSlovakNameTitle, text: $formInput.skName, originalLanguage: originalLanguage, scanTargetLanguage: originalLanguage ?? formInput.nameLanguage)
                    languageNameField(language: "en", title: L10n.AddFood.fieldEnglishNameTitle, text: $formInput.engName, originalLanguage: originalLanguage, scanTargetLanguage: originalLanguage ?? formInput.nameLanguage)
                }
                barcodeRowView
                    .listRowInsets(.vertical, 0)
            }
            .listSectionSpacing(.custom(20))
            Section {
                FoodPhotoPicker(photo: $formInput.photo, isMissing: highlightedFields.contains(.photo)) {
                    onFieldEdited(.photo)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            .listSectionSpacing(.custom(20))
            Section {
                nutritionLabelScanButton
                FoodItemFormFields(
                    formInput: $formInput,
                    highlightedFields: highlightedFields,
                    showsAlcoholByVolumeField: showsAlcoholByVolumeField,
                    onFieldEdited: onFieldEdited
                )
            }
            .listSectionSpacing(.custom(20))
        }
    }

    // MARK: - Functions

    private func languageNameField(language: String, title: String, text: Binding<String>, originalLanguage: String?, scanTargetLanguage: String) -> some View {
        let isOriginal = language == originalLanguage
        let isScanTarget = language == scanTargetLanguage
        return FloatingLabelTextField(
            title: isOriginal ? L10n.AddFood.fieldOriginalNameTitle(title: title) : title,
            text: Binding(
                get: { text.wrappedValue },
                set: {
                    text.wrappedValue = $0
                    if isScanTarget { onFieldEdited(.name) }
                }
            ),
            placeholder: L10n.AddFood.fieldNamePlaceholder,
            isHighlighted: isScanTarget && highlightedFields.contains(.name)
        )
        .listRowInsets(.vertical, 0)
    }

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
            FloatingLabelTextField(
                title: L10n.AddFood.fieldBarcodeTitle,
                text: $formInput.scannedCode,
                message: formInput.scannedCode.isEmpty ? .hint(L10n.AddFood.warningMissingBarcode) : nil,
                keyboardType: .numberPad,
                trailingButton: ProcessInfo.isRunningOnMac ? nil : BadgeButton(
                    imageName: .barCode,
                    accessibilityLabel: L10n.AddFood.nutritionLabelBarcodeScanAccessibility
                ) {
                    onScanTapped()
                }
            )
        }
    }

    @ViewBuilder private var nutritionLabelScanButton: some View {
        if let onNutritionLabelScanTapped, !ProcessInfo.isRunningOnMac {
            Button {
                onNutritionLabelScanTapped()
            } label: {
                Label(L10n.AddFood.buttonScanNutritionLabel, systemImage: BaseImageName.camera.rawValue)
            }
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    List {
        FoodItemFormSections(formInput: .constant(FoodItemFormInput()), barcodeRow: .editable {})
    }
}
#endif
