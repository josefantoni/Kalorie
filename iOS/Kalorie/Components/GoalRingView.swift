//
//  GoalRingView.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import MacroKit
import SwiftUI

struct GoalRingView: View {

    // MARK: - Properties

    let progress: Double?
    var protein: Double = 0
    var carbs: Double = 0
    var fat: Double = 0

    static let size: CGFloat = 72
    static let maxLaps = 3.0

    private let lineWidth: CGFloat = 8

    // MARK: - Body

    var body: some View {
        ZStack {
            Circle()
                .fill(Color(.secondarySystemGroupedBackground))
            Canvas { context, canvasSize in
                drawRing(context: context, size: canvasSize)
            }
            if let progress {
                Text(progress, format: .percent.precision(.fractionLength(0)))
                    .font(.footnote.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .padding(.horizontal, lineWidth + 4)
                    .foregroundStyle(Color.primary)
            } else {
                Text(L10n.Dashboard.goalSetGoal)
                    .font(.caption2.bold())
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .padding(.horizontal, lineWidth + 4)
                    .foregroundStyle(Color.primary)
            }
        }
        .frame(width: Self.size, height: Self.size)
    }

    // MARK: - Functions

    private func drawRing(context: GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = min(size.width, size.height) / 2 - lineWidth / 2 - 2
        let style = StrokeStyle(lineWidth: lineWidth, lineCap: .round)

        var track = Path()
        track.addArc(center: center, radius: radius, startAngle: .degrees(0), endAngle: .degrees(360), clockwise: false)
        context.stroke(track, with: .color(Color.secondary.opacity(0.2)), style: style)

        guard
            let progress,
            progress > 0
        else { return }
        let laps = min(progress, Self.maxLaps)
        let segments = colourSegments(patternLength: min(laps, 1))
        let lapCount = Int(laps.rounded(.up))
        for lap in 0..<lapCount {
            let fill = min(1, laps - Double(lap))
            if lap == 0 {
                drawLap(fill: fill, isFirst: true, segments: segments, in: context, center: center, radius: radius)
            } else {
                context.drawLayer { layer in
                    layer.addFilter(.shadow(color: .black.opacity(0.6), radius: 3, x: 0, y: 0))
                    drawLap(fill: fill, isFirst: false, segments: segments, in: layer, center: center, radius: radius)
                }
            }
        }
    }

    private func colourSegments(patternLength: Double) -> [(start: Double, end: Double, color: Color)] {
        let shares = EnergyKt.energyShares(protein: protein, carbohydrate: carbs, fat: fat).map(\.doubleValue)
        let total = shares.reduce(0, +)
        guard total > 0 else { return [(0, patternLength, Color.secondary)] }
        let ordered: [(Double, Color)] = [
            (shares[2], Color.fat),
            (shares[1], Color.carbs),
            (shares[0], Color.protein)
        ]
        var segments: [(start: Double, end: Double, color: Color)] = []
        var cursor = 0.0
        for (share, color) in ordered where share > 0 {
            let end = cursor + patternLength * share / total
            segments.append((cursor, end, color))
            cursor = end
        }
        return segments
    }

    private func drawLap(
        fill: Double,
        isFirst: Bool,
        segments: [(start: Double, end: Double, color: Color)],
        in context: GraphicsContext,
        center: CGPoint,
        radius: CGFloat
    ) {
        if
            isFirst,
            let first = segments.first
        {
            drawCap(angle: -90, color: first.color, in: context, center: center, radius: radius)
        }
        let flatStyle = StrokeStyle(lineWidth: lineWidth, lineCap: .butt)
        let seamOverlap = 0.004
        var headColor: Color?
        for segment in segments {
            let to = min(segment.end, fill)
            guard to > segment.start else { continue }
            let overlappedTo = to < fill ? min(to + seamOverlap, fill) : to
            var arc = Path()
            arc.addArc(
                center: center,
                radius: radius,
                startAngle: .degrees(-90 + 360 * segment.start),
                endAngle: .degrees(-90 + 360 * overlappedTo),
                clockwise: false
            )
            context.stroke(arc, with: .color(segment.color), style: flatStyle)
            headColor = segment.color
        }
        if let headColor {
            drawCap(angle: -90 + 360 * fill, color: headColor, in: context, center: center, radius: radius)
        }
    }

    private func drawCap(angle: Double, color: Color, in context: GraphicsContext, center: CGPoint, radius: CGFloat) {
        let radians = Angle.degrees(angle).radians
        let point = CGPoint(x: center.x + radius * CGFloat(cos(radians)), y: center.y + radius * CGFloat(sin(radians)))
        let cap = Path(ellipseIn: CGRect(x: point.x - lineWidth / 2, y: point.y - lineWidth / 2, width: lineWidth, height: lineWidth))
        context.fill(cap, with: .color(color))
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    HStack {
        GoalRingView(progress: nil)
        GoalRingView(progress: 0.42, protein: 95, carbs: 210, fat: 58)
        GoalRingView(progress: 1.0, protein: 95, carbs: 210, fat: 58)
        GoalRingView(progress: 1.08, protein: 95, carbs: 210, fat: 58)
        GoalRingView(progress: 2.3, protein: 95, carbs: 210, fat: 58)
    }
}
#endif
