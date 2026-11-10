//
//  MacroSummaryView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.07.2026.
//

import SwiftUI

struct MacroSummaryView<Accessory: View, LeadingAccessory: View>: View {

    // MARK: - Properties

    let macros: DailyMacros
    let goalGrams: GoalMacroGrams?
    let showsGoalPercent: Bool
    let showsGoalBars: Bool
    let accessory: Accessory
    let leadingAccessory: LeadingAccessory

    private let donutSize: CGFloat = 160
    private let accessoryTrailingPadding: CGFloat = 20
    private let maxAccessoryGap: CGFloat = 40

    private var maxOverlayWidth: CGFloat {
        donutSize + 2 * (maxAccessoryGap + WeightCircleView.size) + accessoryTrailingPadding
    }

    // MARK: - Init

    init(
        macros: DailyMacros,
        goalGrams: GoalMacroGrams? = nil,
        showsGoalPercent: Bool = true,
        showsGoalBars: Bool = true,
        @ViewBuilder accessory: () -> Accessory,
        @ViewBuilder leadingAccessory: () -> LeadingAccessory
    ) {
        self.macros = macros
        self.goalGrams = goalGrams
        self.showsGoalPercent = showsGoalPercent
        self.showsGoalBars = showsGoalBars
        self.accessory = accessory()
        self.leadingAccessory = leadingAccessory()
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
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
            .overlay {
                leadingAccessory
                    .frame(width: WeightCircleView.size, height: WeightCircleView.size)
                    .padding(.top, donutSize * 20 / 30 - WeightCircleView.size / 2)
                    .padding(.leading, accessoryTrailingPadding)
                    .frame(maxWidth: maxOverlayWidth, maxHeight: .infinity, alignment: .topLeading)
            }

            HStack {
                Spacer()
                macroLabel(color: Color.protein, name: L10n.FoodQuantity.protein, value: macros.protein, goal: goalGrams?.protein)
                Spacer()
                macroLabel(color: Color.carbs, name: L10n.FoodQuantity.carbs, value: macros.carbs, goal: goalGrams?.carbs)
                Spacer()
                macroLabel(color: Color.fat, name: L10n.FoodQuantity.fat, value: macros.fat, goal: goalGrams?.fat)
                Spacer()
            }
            .padding(.top)

            Divider()
                .padding(.horizontal)
                .padding(.vertical, 12)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                detailRow(label: L10n.AddFood.fieldCarbsSugar, value: macros.carbohydrateSugar, dotColor: Color.carbs)
                detailRow(label: L10n.AddFood.fieldSalt, value: macros.salt)
                detailRow(label: L10n.AddFood.fieldFatSaturated, value: macros.fatSaturated, dotColor: Color.fat)
                detailRow(label: L10n.AddFood.fieldFiber, value: macros.fiber)
            }
            .padding(.horizontal)
        }
        .padding(.top, 4)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Functions

    @ViewBuilder
    private func macroLabel(color: Color, name: String, value: Double, goal: Double?) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                Text(name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value.formattedGrams())
                    .font(.subheadline.bold())
                if let goal {
                    Text("/ \(goal.formattedGrams())")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.top, 4)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            if
                let goal,
                goal > 0
            {
                if showsGoalBars {
                    GoalProgressBar(progress: value / goal, color: color)
                        .frame(width: 72)
                        .padding(.top, 8)
                }
                if showsGoalPercent {
                    Text(L10n.Goals.macroPercentOfGoal((value / goal).formatted(.percent.precision(.fractionLength(0)))))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                }
            }
        }
        .frame(minWidth: 72)
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

struct GoalProgressBar: View {

    // MARK: - Properties

    let progress: Double
    let color: Color

    // MARK: - Body

    var body: some View {
        Capsule()
            .fill(Color.secondary.opacity(0.2))
            .frame(height: 4)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Capsule()
                        .fill(color)
                        .frame(width: proxy.size.width * min(max(progress, 0), 1))
                }
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
        ),
        goalGrams: GoalMacroGrams(protein: 120, carbs: 250, fat: 70)
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
    } leadingAccessory: {
        GoalRingView(progress: 1.08, protein: 95, carbs: 210, fat: 58)
    }
}
#endif
