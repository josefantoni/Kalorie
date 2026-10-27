//
//  BodyWeightChartView.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import SwiftUI

struct BodyWeightChartView: View {

    // MARK: - Properties

    let points: [BodyWeightChartPoint]
    let period: DateInterval
    let unit: BodyWeightUnit

    private let minimumSpanKilograms = 2.0
    private let dotRadius: CGFloat = 3
    private let verticalInset: CGFloat = 7
    private let chartHeight: CGFloat = 160

    // MARK: - Body

    var body: some View {
        VStack(spacing: 4) {
            HStack(alignment: .top, spacing: 8) {
                axisLabels
                Canvas { context, size in
                    draw(in: &context, size: size)
                }
                .frame(height: chartHeight)
            }
            HStack {
                Text(period.start.formatted(date: .abbreviated, time: .omitted))
                Spacer()
                Text(period.end.addingTimeInterval(-1).formatted(date: .abbreviated, time: .omitted))
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
            .padding(.leading, 44)
        }
        .accessibilityHidden(true)
    }

    private var yRange: ClosedRange<Double> {
        let values = points.map { unit.display(kilograms: $0.kilograms) }
        guard
            let low = values.min(),
            let high = values.max()
        else { return 0...1 }
        let minimumSpan = unit.display(kilograms: minimumSpanKilograms) - unit.display(kilograms: 0)
        let span = max(high - low, minimumSpan)
        let middle = (high + low) / 2
        return (middle - span / 2)...(middle + span / 2)
    }

    private var axisLabels: some View {
        Canvas { context, size in
            drawAxisLabels(in: &context, size: size)
        }
        .frame(width: 36, height: chartHeight)
    }

    // MARK: - Functions

    private func position(of point: BodyWeightChartPoint, in size: CGSize, range: ClosedRange<Double>) -> CGPoint {
        let duration = max(period.duration, 1)
        let fractionX = point.date.timeIntervalSince(period.start) / duration
        let plotWidth = size.width - 2 * dotRadius
        return CGPoint(
            x: dotRadius + plotWidth * fractionX,
            y: yPosition(of: unit.display(kilograms: point.kilograms), height: size.height, range: range)
        )
    }

    private func yPosition(of displayValue: Double, height: CGFloat, range: ClosedRange<Double>) -> CGFloat {
        let fractionY = (displayValue - range.lowerBound) / (range.upperBound - range.lowerBound)
        return verticalInset + (height - 2 * verticalInset) * (1 - fractionY)
    }

    private func draw(in context: inout GraphicsContext, size: CGSize) {
        let range = yRange
        var guides = Path()
        for (_, offsetY) in labelledValues(height: size.height, range: range) {
            guides.move(to: CGPoint(x: 0, y: offsetY))
            guides.addLine(to: CGPoint(x: size.width, y: offsetY))
        }
        context.stroke(guides, with: .color(.secondary.opacity(0.15)), lineWidth: 0.5)

        var grid = Path()
        grid.move(to: CGPoint(x: 0, y: size.height - verticalInset))
        grid.addLine(to: CGPoint(x: size.width, y: size.height - verticalInset))
        context.stroke(grid, with: .color(.secondary.opacity(0.3)), lineWidth: 1)

        let positions = points.map { position(of: $0, in: size, range: range) }
        var line = Path()
        for (index, position) in positions.enumerated() {
            if index == 0 {
                line.move(to: position)
            } else {
                line.addLine(to: position)
            }
        }
        context.stroke(line, with: .color(Color(.systemGray)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        for position in positions {
            context.fill(
                Path(ellipseIn: CGRect(x: position.x - dotRadius, y: position.y - dotRadius, width: 2 * dotRadius, height: 2 * dotRadius)),
                with: .color(Color(.systemGray))
            )
        }
    }

    private func labelledValues(height: CGFloat, range: ClosedRange<Double>) -> [(value: Double, offsetY: CGFloat)] {
        let values = points.map { unit.display(kilograms: $0.kilograms) }
        guard
            let highest = values.indices.max(by: { values[$0] < values[$1] }),
            let lowest = values.indices.min(by: { values[$0] < values[$1] })
        else { return [] }
        var labelled: [(value: Double, offsetY: CGFloat)] = []
        var visited = Set<Int>()
        for index in [highest, lowest, values.count - 1, 0] + Array(values.indices) where visited.insert(index).inserted {
            let offsetY = yPosition(of: values[index], height: height, range: range)
            if labelled.contains(where: { abs($0.offsetY - offsetY) < 14 }) { continue }
            labelled.append((values[index], offsetY))
        }
        return labelled
    }

    private func drawAxisLabels(in context: inout GraphicsContext, size: CGSize) {
        for (value, offsetY) in labelledValues(height: size.height, range: yRange) {
            let label = context.resolve(
                Text(value.formatted(.number.precision(.fractionLength(1))))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            )
            context.draw(label, at: CGPoint(x: size.width, y: offsetY), anchor: .trailing)
        }
    }
}
