//
//  MealSectionMacroView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.07.2026.
//

import SwiftUI

struct MealSectionMacroView: View {

    // MARK: - Properties

    let name: String
    let foods: [FoodConsumedDomain]

    private var macros: DailyMacros { DailyMacros(foods: foods) }

    // MARK: - Body

    var body: some View {
        let macros = macros
        VStack(alignment: .leading, spacing: 8) {
            Text(name)
                .font(.headline)
            Divider()

            MacroDonutView(protein: macros.protein, carbs: macros.carbs, fat: macros.fat, calories: macros.calories, size: 120)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)

            Divider()
            macroRow(label: L10n.FoodQuantity.protein, value: macros.protein.formattedGrams())
            macroRow(label: L10n.FoodQuantity.carbs, value: macros.carbs.formattedGrams())
            macroRow(label: L10n.AddFood.fieldCarbsSugar, value: macros.carbohydrateSugar.formattedGrams(), indented: true)
            macroRow(label: L10n.FoodQuantity.fat, value: macros.fat.formattedGrams())
            macroRow(label: L10n.AddFood.fieldFatUnsaturated, value: macros.fatUnsaturated.formattedGrams(), indented: true)
            macroRow(label: L10n.AddFood.fieldFiber, value: macros.fiber.formattedGrams())
        }
        .padding()
        .frame(minWidth: 200)
    }

    // MARK: - Functions

    private func macroRow(label: String, value: String, indented: Bool = false) -> some View {
        HStack {
            if indented { Spacer().frame(width: 12) }
            Text(label)
                .foregroundStyle(indented ? .tertiary : .secondary)
            Spacer()
            Text(value)
                .bold()
                .foregroundStyle(indented ? .secondary : .primary)
        }
        .font(indented ? .caption : .subheadline)
    }
}
