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
    private var fat: Double { foods.reduce(0) { $0 + $1.fat } }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(name)
                .font(.headline)
            Divider()
            macroRow(label: L10n.FoodQuantity.calories, value: "\(calories) kcal")
            macroRow(label: L10n.FoodQuantity.protein, value: String(format: "%.1f g", protein))
            macroRow(label: L10n.FoodQuantity.carbs, value: String(format: "%.1f g", carbs))
            macroRow(label: L10n.FoodQuantity.fat, value: String(format: "%.1f g", fat))
        }
        .padding()
        .frame(minWidth: 200)
    }

    // MARK: - Functions

    private func macroRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .bold()
        }
        .font(.subheadline)
    }
}
