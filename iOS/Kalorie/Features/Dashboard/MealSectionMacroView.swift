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
                .foregroundStyle(Color(.label))
            Divider()

            MacroDonutView(protein: macros.protein, carbs: macros.carbs, fat: macros.fat, calories: macros.calories, size: 120)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)

            Divider()
            macroRow(label: L10n.FoodQuantity.protein, value: macros.protein.formattedGrams(), dotColor: Color.protein)
            macroRow(label: L10n.FoodQuantity.carbs, value: macros.carbs.formattedGrams(), dotColor: Color.carbs)
            macroRow(label: L10n.AddFood.fieldCarbsSugar, value: macros.carbohydrateSugar.formattedGrams(), dotColor: Color.carbs, indented: true)
            macroRow(label: L10n.FoodQuantity.fat, value: macros.fat.formattedGrams(), dotColor: Color.fat)
            macroRow(label: L10n.AddFood.fieldFatUnsaturated, value: macros.fatUnsaturated.formattedGrams(), dotColor: Color.fat, indented: true)
            macroRow(label: L10n.AddFood.fieldFiber, value: macros.fiber.formattedGrams())
            macroRow(label: L10n.AddFood.fieldSalt, value: macros.salt.formattedGrams())
        }
        .padding()
        .frame(minWidth: 200)
    }

    // MARK: - Functions

    private func macroRow(label: String, value: String, dotColor: Color? = nil, indented: Bool = false) -> some View {
        HStack(spacing: 4) {
            if indented { Spacer().frame(width: 12) }
            Circle()
                .fill(dotColor ?? .clear)
                .frame(width: indented ? 6 : 8, height: indented ? 6 : 8)
            Text(label)
            Spacer()
            Text(value)
                .bold()
        }
        .foregroundStyle(Color(.label))
        .font(indented ? .caption : .subheadline)
    }
}
