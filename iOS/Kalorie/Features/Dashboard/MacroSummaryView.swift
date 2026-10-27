//
//  MacroSummaryView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.07.2026.
//

import SwiftUI

struct MacroSummaryView<Accessory: View>: View {

    // MARK: - Properties

    let macros: DailyMacros
    let accessory: Accessory

    private let donutSize: CGFloat = 160
    private let accessoryTrailingPadding: CGFloat = 20
    private let maxAccessoryGap: CGFloat = 40

    private var maxOverlayWidth: CGFloat {
        donutSize + 2 * (maxAccessoryGap + WeightCircleView.size) + accessoryTrailingPadding
    }

    // MARK: - Init

    init(macros: DailyMacros, @ViewBuilder accessory: () -> Accessory) {
        self.macros = macros
        self.accessory = accessory()
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 16) {
            MacroDonutView(
                protein: macros.protein,
                carbs: macros.carbs,
                fat: macros.fat,
                calories: macros.calories,
                size: donutSize
            )
            .frame(maxWidth: .infinity)
            .overlay {
                accessory
                    .frame(width: WeightCircleView.size, height: WeightCircleView.size)
                    .padding(.top, donutSize * 20 / 30 - WeightCircleView.size / 2)
                    .padding(.trailing, accessoryTrailingPadding)
                    .frame(maxWidth: maxOverlayWidth, maxHeight: .infinity, alignment: .topTrailing)
            }

            HStack {
                Spacer()
                macroLabel(color: Color.protein, name: L10n.FoodQuantity.protein, value: macros.protein)
                Spacer()
                macroLabel(color: Color.carbs, name: L10n.FoodQuantity.carbs, value: macros.carbs)
                Spacer()
                macroLabel(color: Color.fat, name: L10n.FoodQuantity.fat, value: macros.fat)
                Spacer()
            }

            Divider()
                .padding(.horizontal)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                detailRow(label: L10n.AddFood.fieldCarbsSugar, value: macros.carbohydrateSugar, dotColor: Color.carbs)
                detailRow(label: L10n.AddFood.fieldSalt, value: macros.salt)
                detailRow(label: L10n.AddFood.fieldFatSaturated, value: macros.fatSaturated, dotColor: Color.fat)
                detailRow(label: L10n.AddFood.fieldFiber, value: macros.fiber)
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
            Text(value.formattedGrams())
                .font(.subheadline.bold())
        }
    }

    private func detailRow(label: String, value: Double, dotColor: Color? = nil) -> some View {
        HStack(spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                if let dotColor {
                    Circle()
                        .fill(dotColor)
                        .frame(width: 6, height: 6)
                }
                Text(label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Text(value.formattedGrams())
                .font(.caption.bold())
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    MacroSummaryView(
        macros: DailyMacros(
            calories: 1840,
            protein: 95,
            carbs: 210,
            carbohydrateSugar: 48,
            fat: 58,
            fatSaturated: 22,
            fiber: 18,
            salt: 3.2
        )
    ) {
        WeightCircleView(
            weights: [
                BodyWeightDomain(dayKey: "2026-10-27", kilograms: 78.4),
                BodyWeightDomain(dayKey: "2026-10-20", kilograms: 79.1),
                BodyWeightDomain(dayKey: "2026-10-13", kilograms: 79.0)
            ],
            laterWeights: [BodyWeightDomain(dayKey: "2026-10-28", kilograms: 77.9)],
            unit: .kg
        )
    }
}
#endif
