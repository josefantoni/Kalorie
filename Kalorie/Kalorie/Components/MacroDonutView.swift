//
//  MacroDonutView.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import SwiftUI

struct MacroDonutView: View {

    // MARK: - Properties

    let protein: Double
    let carbs: Double
    let fat: Double
    let calories: Int
    let size: CGFloat

    // MARK: - Body

    var body: some View {
        ZStack {
            Canvas { context, canvasSize in
                drawDonut(context: context, size: canvasSize)
            }
            .frame(width: size, height: size)

            VStack(spacing: 0) {
                Text("\(calories)")
                    .font(.title3.bold())
                Text("kcal")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Functions

    private func drawDonut(context: GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let outerRadius = min(size.width, size.height) / 2 - 2
        let innerRadius = outerRadius * 0.58
        let total = protein + carbs + fat

        guard total > 0 else {
            var ring = Path()
            ring.addArc(center: center, radius: outerRadius, startAngle: .degrees(0), endAngle: .degrees(360), clockwise: false)
            ring.addArc(center: center, radius: innerRadius, startAngle: .degrees(360), endAngle: .degrees(0), clockwise: true)
            ring.closeSubpath()
            context.fill(ring, with: .color(.gray.opacity(0.2)))
            return
        }

        let slices: [(Double, Color)] = [
            (protein, .blue),
            (carbs, .orange),
            (fat, .pink)
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
                    x: center.x + midRadius * CGFloat(cos(midAngle.radians)),
                    y: center.y + midRadius * CGFloat(sin(midAngle.radians))
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
}
