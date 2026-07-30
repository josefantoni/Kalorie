//
//  MacroSummaryView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.07.2026.
//

import SwiftUI

struct MacroSummaryView: View {

    // MARK: - Properties

    let macros: DailyMacros

    // MARK: - Body

    var body: some View {
        VStack(spacing: 16) {
            MacroDonutView(
                protein: macros.protein,
                carbs: macros.carbs,
                fat: macros.fat,
                calories: macros.calories,
                size: 160
            )

            HStack {
                Spacer()
                macroLabel(color: .blue, name: L10n.FoodQuantity.protein, value: macros.protein)
                Spacer()
                macroLabel(color: .orange, name: L10n.FoodQuantity.carbs, value: macros.carbs)
                Spacer()
                macroLabel(color: .pink, name: L10n.FoodQuantity.fat, value: macros.fat)
                Spacer()
            }

            Divider()
                .padding(.horizontal)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                detailRow(label: L10n.AddFood.fieldCarbsSugar, value: macros.carbohydrateSugar, dotColor: .orange)
                detailRow(label: L10n.AddFood.fieldFatUnsaturated, value: macros.fatUnsaturated, dotColor: .pink)
                detailRow(label: L10n.AddFood.fieldFiber, value: macros.fiber)
                detailRow(label: L10n.AddFood.fieldSalt, value: macros.salt)
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Functions

    @ViewBuilder
    private func macroLabel(color: Color, name: String, value: Double) -> some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                Text(name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(String(format: "%.1f g", value))
                .font(.subheadline.bold())
        }
    }

    private func detailRow(label: String, value: Double, dotColor: Color? = nil) -> some View {
        HStack(spacing: 4) {
            if let dotColor {
                Circle()
                    .fill(dotColor)
                    .frame(width: 6, height: 6)
            }
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer()
            Text(String(format: "%.1f g", value))
                .font(.caption.bold())
        }
    }
}

// MARK: - Preview

#Preview {
    MacroSummaryView(
        macros: DailyMacros(
            calories: 1840,
            protein: 95,
            carbs: 210,
            carbohydrateSugar: 48,
            fat: 58,
            fatUnsaturated: 22,
            fiber: 18,
            salt: 3.2
        )
    )
}
