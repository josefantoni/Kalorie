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
        VStack(alignment: .leading, spacing: 8) {
            Text(name)
                .font(.headline)
            Divider()

            MacroDonutView(protein: macros.protein, carbs: macros.carbs, fat: macros.fat, calories: macros.calories, size: 120)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)

            Divider()
            macroRow(label: L10n.FoodQuantity.protein, value: String(format: "%.1f g", macros.protein))
            macroRow(label: L10n.FoodQuantity.carbs, value: String(format: "%.1f g", macros.carbs))
            macroRow(label: L10n.AddFood.fieldCarbsSugar, value: String(format: "%.1f g", macros.carbohydrateSugar), indented: true)
            macroRow(label: L10n.FoodQuantity.fat, value: String(format: "%.1f g", macros.fat))
            macroRow(label: L10n.AddFood.fieldFatUnsaturated, value: String(format: "%.1f g", macros.fatUnsaturated), indented: true)
            macroRow(label: L10n.AddFood.fieldFiber, value: String(format: "%.1f g", macros.fiber))
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
