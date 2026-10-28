//
//  WeightCircleView.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import SwiftUI

struct WeightCircleView: View {

    // MARK: - Properties

    let weights: [BodyWeightDomain]
    let laterWeights: [BodyWeightDomain]
    let unit: BodyWeightUnit

    static let size: CGFloat = 72

    private var points: [Double] {
        BodyWeightWindow(earlier: weights, later: laterWeights).chronological.map { unit.display(kilograms: $0.kilograms) }
    }

    private var sparkline: some View {
        let shape = WeightSparkline(points: points, centerIndex: weights.count - 1, minimumSpan: unit.display(kilograms: 2) - unit.display(kilograms: 0))
        return shape
            .stroke(Color.secondary, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            .overlay {
                Circle()
                    .fill(Color.primary)
                    .frame(width: 6, height: 6)
            }
            .frame(width: 56, height: 16)
    }

    // MARK: - Body

    var body: some View {
        ZStack {
            Circle()
                .fill(Color(.secondarySystemGroupedBackground))
            if let latest = weights.first {
                VStack(spacing: 2) {
                    Text(unit.formatted(kilograms: latest.kilograms))
                        .font(.footnote.bold())
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(.horizontal, 6)
                    if points.count >= 2 {
                        sparkline
                    }
                }
            } else {
                Capsule()
                    .fill(Color.secondary.opacity(0.3))
                    .frame(width: 36, height: 10)
                    .blur(radius: 3)
            }
        }
        .frame(width: Self.size, height: Self.size)
    }
}

struct WeightSparkline: Shape {

    // MARK: - Properties

    let points: [Double]
    let centerIndex: Int
    let minimumSpan: Double

    // MARK: - Functions

    func positions(in rect: CGRect) -> [CGPoint] {
        guard
            points.count >= 2,
            points.indices.contains(centerIndex)
        else { return [] }
        let center = points[centerIndex]
        let deviation = points.map { abs($0 - center) }.max() ?? 0
        let halfSpan = max(deviation, minimumSpan / 2)
        let rightCount = points.count - 1 - centerIndex
        let step = rect.width / 2 / CGFloat(max(centerIndex, rightCount, 1))
        return points.enumerated().map { index, value in
            CGPoint(
                x: rect.midX + step * CGFloat(index - centerIndex),
                y: rect.midY - rect.height / 2 * CGFloat((value - center) / halfSpan)
            )
        }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        for (index, position) in positions(in: rect).enumerated() {
            if index == 0 {
                path.move(to: position)
            } else {
                path.addLine(to: position)
            }
        }
        return path
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    HStack {
        WeightCircleView(weights: [], laterWeights: [], unit: .kg)
        WeightCircleView(weights: [BodyWeightDomain(dayKey: "2026-10-27", kilograms: 78.4)], laterWeights: [], unit: .kg)
        WeightCircleView(
            weights: [
                BodyWeightDomain(dayKey: "2026-10-27", kilograms: 78.4),
                BodyWeightDomain(dayKey: "2026-10-20", kilograms: 79.1),
                BodyWeightDomain(dayKey: "2026-10-13", kilograms: 79.0)
            ],
            laterWeights: [BodyWeightDomain(dayKey: "2026-10-28", kilograms: 77.9)],
            unit: .lb
        )
    }
}
#endif
