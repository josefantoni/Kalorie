//
//  FoodItemFormFields.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

struct FoodItemFormFields: View {

    // MARK: - Properties

    @Binding var formInput: FoodItemFormInput

    // MARK: - Body

    var body: some View {
        Group {
            BaseStringTextField(
                placeholder: L10n.AddFood.fieldNamePlaceholder,
                title: L10n.AddFood.fieldNameTitle,
                text: $formInput.name
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldWeight,
                unit: L10n.Common.unitGrams,
                weight: $formInput.weightOfProduct
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldEnergyKJ,
                unit: "kJ",
                weight: $formInput.energyKJ
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCaloriesPer100g,
                unit: "kcal",
                weight: $formInput.caloriesPerHundredGrams
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldProtein,
                unit: L10n.Common.unitGrams,
                weight: $formInput.protein
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbs,
                unit: L10n.Common.unitGrams,
                weight: $formInput.carbohydrate
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldCarbsSugar,
                unit: L10n.Common.unitGrams,
                weight: $formInput.carbohydratePureSugar
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFiber,
                unit: L10n.Common.unitGrams,
                weight: Binding(
                    get: { formInput.fiber ?? 0 },
                    set: { formInput.fiber = $0 }
                )
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFat,
                unit: L10n.Common.unitGrams,
                weight: $formInput.fat
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatSaturated,
                unit: L10n.Common.unitGrams,
                weight: Binding(
                    get: { formInput.fatSaturated ?? 0 },
                    set: { formInput.fatSaturated = $0 }
                )
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldFatUnsaturated,
                unit: L10n.Common.unitGrams,
                weight: $formInput.fatUnsaturatedFattyAcids
            )
            BaseDoubleTextField(
                title: L10n.AddFood.fieldSalt,
                unit: L10n.Common.unitGrams,
                weight: $formInput.salt
            )
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemFormFields(formInput: .constant(FoodItemFormInput()))
    }
}
