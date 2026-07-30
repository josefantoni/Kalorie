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

    private var calories: Int { foods.reduce(0) { $0 + $1.calories } }
    private var protein: Double { foods.reduce(0) { $0 + $1.protein } }
    private var carbs: Double { foods.reduce(0) { $0 + $1.carbohydrate } }
    private var sugar: Double { foods.reduce(0) { $0 + $1.carbohydrateSugar } }
    private var fat: Double { foods.reduce(0) { $0 + $1.fat } }
    private var fatUnsaturated: Double { foods.reduce(0) { $0 + $1.fatUnsaturated } }
    private var fiber: Double { foods.reduce(0) { $0 + $1.fiber } }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(name)
                .font(.headline)
            Divider()

            MacroDonutView(protein: protein, carbs: carbs, fat: fat, calories: calories, size: 120)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)

            Divider()
            macroRow(label: L10n.FoodQuantity.protein, value: String(format: "%.1f g", protein))
            macroRow(label: L10n.FoodQuantity.carbs, value: String(format: "%.1f g", carbs))
            macroRow(label: L10n.AddFood.fieldCarbsSugar, value: String(format: "%.1f g", sugar), indented: true)
            macroRow(label: L10n.FoodQuantity.fat, value: String(format: "%.1f g", fat))
            macroRow(label: L10n.AddFood.fieldFatUnsaturated, value: String(format: "%.1f g", fatUnsaturated), indented: true)
            macroRow(label: L10n.AddFood.fieldFiber, value: String(format: "%.1f g", fiber))
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
