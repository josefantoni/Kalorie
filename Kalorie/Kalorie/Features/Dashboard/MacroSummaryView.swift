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
            ZStack {
                Canvas { context, size in
                    drawDonut(context: context, size: size)
                }
                .frame(width: 160, height: 160)

                VStack(spacing: 0) {
                    Text("\(macros.calories)")
                        .font(.title3.bold())
                    Text("kcal")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

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

    private func drawDonut(context: GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let outerRadius = min(size.width, size.height) / 2 - 2
        let innerRadius = outerRadius * 0.58
        let total = macros.protein + macros.carbs + macros.fat

        guard total > 0 else {
            var ring = Path()
            ring.addArc(center: center, radius: outerRadius, startAngle: .degrees(0), endAngle: .degrees(360), clockwise: false)
            ring.addArc(center: center, radius: innerRadius, startAngle: .degrees(360), endAngle: .degrees(0), clockwise: true)
            ring.closeSubpath()
            context.fill(ring, with: .color(.gray.opacity(0.2)))
            return
        }

        let slices: [(Double, Color)] = [
            (macros.protein, .blue),
            (macros.carbs, .orange),
            (macros.fat, .pink)
        ]

        let midRadius = (outerRadius + innerRadius) / 2
        var startAngle = Angle(degrees: -90)
        for (value, color) in slices where value > 0 {
            let endAngle = startAngle + .degrees(360 * value / total)
            var path = Path()
            path.addArc(center: center, radius: outerRadius, startAngle: startAngle, endAngle: endAngle, clockwise: false)
            path.addArc(center: center, radius: innerRadius, startAngle: endAngle, endAngle: startAngle, clockwise: true)
            path.closeSubpath()
            context.fill(path, with: .color(color))

            let pct = Int((value / total * 100).rounded())
            if pct >= 8 {
                let midAngle = startAngle + .degrees(180 * value / total)
                let textCenter = CGPoint(
                    x: center.x + midRadius * cos(midAngle.radians),
                    y: center.y + midRadius * sin(midAngle.radians)
                )
                context.draw(
                    Text("\(pct)%")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white),
                    at: textCenter
                )
            }

            startAngle = endAngle
        }
    }

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
