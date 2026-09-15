//
//  FoodItemFormFields.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

enum FoodItemFormField: CaseIterable {
    case name, weight, energyKJ, calories, protein, carbohydrate, carbohydrateSugar, fiber, fat, fatSaturated, fatUnsaturated, salt
}

struct FoodItemFormFields: View {

    // MARK: - Properties

    @Binding var formInput: FoodItemFormInput
    var highlightedFields: Set<FoodItemFormField> = []
    var onFieldEdited: (FoodItemFormField) -> Void = { _ in }

    // MARK: - Body

    var body: some View {
        Group {
            BaseDoubleTextField(
                title: L10n.AddFood.fieldWeight,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.weightOfProduct, field: .weight),
                isHighlighted: highlightedFields.contains(.weight)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldEnergyKJ,
                unit: "kJ",
                weight: doubleBinding(\.energyKJ, field: .energyKJ),
                isHighlighted: highlightedFields.contains(.energyKJ)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCaloriesPer100g,
                unit: "kcal",
                weight: doubleBinding(\.caloriesPerHundredGrams, field: .calories),
                isHighlighted: highlightedFields.contains(.calories)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldProtein,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.protein, field: .protein),
                isHighlighted: highlightedFields.contains(.protein)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbs,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.carbohydrate, field: .carbohydrate),
                isHighlighted: highlightedFields.contains(.carbohydrate)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbsSugar,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.carbohydratePureSugar, field: .carbohydrateSugar),
                isHighlighted: highlightedFields.contains(.carbohydrateSugar)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFiber,
                unit: L10n.Common.unitGrams,
                weight: optionalDoubleBinding(\.fiber, field: .fiber),
                isHighlighted: highlightedFields.contains(.fiber)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFat,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.fat, field: .fat),
                isHighlighted: highlightedFields.contains(.fat)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatSaturated,
                unit: L10n.Common.unitGrams,
                weight: optionalDoubleBinding(\.fatSaturated, field: .fatSaturated),
                isHighlighted: highlightedFields.contains(.fatSaturated)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatUnsaturated,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.fatUnsaturatedFattyAcids, field: .fatUnsaturated),
                isHighlighted: highlightedFields.contains(.fatUnsaturated)
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldSalt,
                unit: L10n.Common.unitGrams,
                weight: doubleBinding(\.salt, field: .salt),
                isHighlighted: highlightedFields.contains(.salt)
            )
        }
    }

    // MARK: - Functions

    private func doubleBinding(_ keyPath: WritableKeyPath<FoodItemFormInput, Double>, field: FoodItemFormField) -> Binding<Double> {
        Binding(
            get: { formInput[keyPath: keyPath] },
            set: { formInput[keyPath: keyPath] = $0; onFieldEdited(field) }
        )
    }

    private func optionalDoubleBinding(_ keyPath: WritableKeyPath<FoodItemFormInput, Double?>, field: FoodItemFormField) -> Binding<Double> {
        Binding(
            get: { formInput[keyPath: keyPath] ?? 0 },
            set: { formInput[keyPath: keyPath] = $0; onFieldEdited(field) }
        )
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemFormFields(formInput: .constant(FoodItemFormInput()))
    }
}
