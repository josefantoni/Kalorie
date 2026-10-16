//
//  LunchIllustrationView.swift
//  Kalorie
//
//  Created by Josef Antoni on 16.10.2026.
//

import SwiftUI

private struct PlateBowlShape: Shape {

    // MARK: - Functions

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addCurve(
            to: CGPoint(x: rect.maxX * 0.8, y: rect.maxY),
            control1: CGPoint(x: rect.maxX * 0.97, y: rect.maxY * 0.7),
            control2: CGPoint(x: rect.maxX * 0.9, y: rect.maxY)
        )
        path.addLine(to: CGPoint(x: rect.maxX * 0.2, y: rect.maxY))
        path.addCurve(
            to: CGPoint(x: rect.minX, y: rect.minY),
            control1: CGPoint(x: rect.maxX * 0.1, y: rect.maxY),
            control2: CGPoint(x: rect.maxX * 0.03, y: rect.maxY * 0.7)
        )
        return path
    }
}

private struct GlassShape: Shape {

    // MARK: - Functions

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX * 0.88, y: rect.maxY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX * 0.12, y: rect.maxY),
            control: CGPoint(x: rect.midX, y: rect.maxY * 1.06)
        )
        path.closeSubpath()
        return path
    }
}

struct LunchIllustrationView: View {

    // MARK: - Properties

    @State private var start = Date()

    // MARK: - Body

    var body: some View {
        TimelineView(.animation) { context in
            LunchIllustrationScene(time: context.date.timeIntervalSince(start))
        }
    }
}

struct LunchIllustrationScene: View {

    // MARK: - Properties

    let time: Double

    // MARK: - Body

    var body: some View {
        ZStack {
            shadow
            lemonade.scaleEffect(popScale(delay: 0.45)).offset(x: 112, y: 28)
            plate.scaleEffect(popScale(delay: 0)).offset(x: -30, y: 60)
            Ellipse()
                .fill(Color.primary.opacity(0.14))
                .frame(width: 110, height: 12)
                .offset(x: -50, y: 62)
            chicken.scaleEffect(popScale(delay: 0.15)).offset(x: -50, y: 28)
            ForEach(0..<3, id: \.self) { index in
                steamPuff(index: index)
            }
            bread.scaleEffect(popScale(delay: 0.3)).offset(x: 38, y: 42)
        }
        .frame(width: 320, height: 200)
    }

    // MARK: - Private

    private func popScale(delay: Double) -> CGFloat {
        let shifted = min(max((time - delay) / 0.5, 0), 1) - 1
        return CGFloat(1 + 2.70158 * shifted * shifted * shifted + 1.70158 * shifted * shifted)
    }

    private func steamPuff(index: Int) -> some View {
        let period = 2.4
        let phase = ((time + Double(index) * period / 3).truncatingRemainder(dividingBy: period)) / period
        let fadeIn = min(max((time - 0.6) / 0.4, 0), 1)
        return Circle()
            .fill(Color.primary.opacity(0.16 * (1 - phase) * fadeIn))
            .frame(width: 10 + 16 * phase, height: 10 + 16 * phase)
            .offset(
                x: -50 + CGFloat(index - 1) * 20 + CGFloat(sin(phase * .pi * 2 + Double(index))) * 6,
                y: 18 - 56 * phase
            )
    }

    private func bubble(index: Int) -> some View {
        let period = 1.6 + Double(index) * 0.3
        let phase = ((time + Double(index) * 0.45).truncatingRemainder(dividingBy: period)) / period
        return Circle()
            .fill(Color.plate.opacity(0.7 * sin(phase * .pi)))
            .frame(width: 5 + CGFloat(index % 2) * 2, height: 5 + CGFloat(index % 2) * 2)
            .offset(
                x: CGFloat(index) * 10 - 15 + CGFloat(sin(phase * .pi * 2 + Double(index))) * 3,
                y: 32 - 64 * CGFloat(phase)
            )
    }

    private var shadow: some View {
        Ellipse()
            .fill(Color.primary.opacity(0.1))
            .frame(width: 300, height: 22)
            .offset(y: 86)
    }

    private var plate: some View {
        ZStack {
            PlateBowlShape()
                .fill(Color.plate)
                .overlay { PlateBowlShape().fill(Color.primary.opacity(0.08)) }
                .frame(width: 190, height: 22)
                .offset(y: 8)
            Ellipse()
                .fill(Color.plate)
                .frame(width: 200, height: 22)
                .overlay { Ellipse().stroke(Color.primary.opacity(0.12), lineWidth: 1.5) }
            Ellipse()
                .fill(Color.primary.opacity(0.07))
                .frame(width: 140, height: 12)
        }
    }

    private var chicken: some View {
        ZStack {
            leg(color: Color.chickenShade)
                .offset(x: 30, y: -12)
            Ellipse()
                .fill(Color.chickenRoast)
                .frame(width: 118, height: 70)
            Ellipse()
                .fill(Color.chickenShade)
                .frame(width: 118, height: 70)
                .mask { Ellipse().frame(width: 118, height: 70).offset(y: 24) }
                .opacity(0.45)
            Ellipse()
                .fill(Color.bone.opacity(0.32))
                .frame(width: 56, height: 18)
                .rotationEffect(.degrees(-14))
                .offset(x: -14, y: -18)
            Ellipse()
                .fill(Color.chickenShade)
                .frame(width: 26, height: 16)
                .rotationEffect(.degrees(24))
                .offset(x: -42, y: 2)
            ForEach(0..<4, id: \.self) { index in
                Circle()
                    .fill(Color.chickenShade.opacity(0.55))
                    .frame(width: 5, height: 5)
                    .offset(x: CGFloat(index) * 17 - 22, y: CGFloat(index % 2) * 14 - 4)
            }
            leg(color: Color.chickenRoast)
                .offset(x: 22, y: -8)
        }
        .frame(width: 150, height: 100)
    }

    private func leg(color: Color) -> some View {
        ZStack {
            Capsule()
                .fill(Color.bone)
                .frame(width: 30, height: 8)
                .offset(x: 30)
            Circle()
                .fill(Color.bone)
                .frame(width: 11, height: 11)
                .offset(x: 46, y: -4)
            Circle()
                .fill(Color.bone)
                .frame(width: 11, height: 11)
                .offset(x: 46, y: 4)
            Ellipse()
                .fill(color)
                .frame(width: 44, height: 28)
        }
        .rotationEffect(.degrees(-50))
    }

    private var bread: some View {
        ZStack {
            Ellipse()
                .fill(Color.primary.opacity(0.14))
                .frame(width: 60, height: 8)
                .offset(y: 20)
            Ellipse()
                .fill(Color.baguetteCrust)
                .frame(width: 62, height: 42)
            Ellipse()
                .fill(Color.breadCrumb.opacity(0.45))
                .frame(width: 30, height: 10)
                .rotationEffect(.degrees(-12))
                .offset(x: -8, y: -11)
            ForEach(0..<3, id: \.self) { index in
                Capsule()
                    .fill(Color.baguetteScore)
                    .frame(width: 4, height: 17)
                    .rotationEffect(.degrees(Double(index - 1) * 38))
                    .offset(x: CGFloat(index - 1) * 11, y: -2 + CGFloat(abs(index - 1)) * 3)
            }
        }
        .frame(width: 70, height: 50)
    }

    private var lemonade: some View {
        ZStack {
            Capsule()
                .fill(Color.baguetteScore)
                .frame(width: 6, height: 74)
                .rotationEffect(.degrees(14 + 4 * sin(time * 2)))
                .offset(x: 10, y: -26)
            GlassShape()
                .fill(Color.lemonade)
                .frame(width: 60, height: 82)
            GlassShape()
                .fill(Color.plate.opacity(0.25))
                .frame(width: 60, height: 82)
                .mask { Rectangle().frame(width: 60, height: 14).offset(y: -34) }
            ForEach(0..<2, id: \.self) { index in
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.plate.opacity(0.55))
                    .frame(width: 20, height: 20)
                    .rotationEffect(.degrees(index == 0 ? 14 : -10))
                    .offset(x: index == 0 ? -10 : 10, y: index == 0 ? -12 : 6)
            }
            ForEach(0..<4, id: \.self) { index in
                bubble(index: index)
            }
            GlassShape()
                .stroke(Color.plate, lineWidth: 2.5)
                .frame(width: 60, height: 82)
            Capsule()
                .fill(Color.plate.opacity(0.7))
                .frame(width: 4, height: 50)
                .offset(x: -19, y: 2)
            lemonSlice.offset(x: 30, y: -38)
        }
        .frame(width: 90, height: 120)
    }

    private var lemonSlice: some View {
        ZStack {
            Circle()
                .fill(Color.lemonSlice)
                .frame(width: 30, height: 30)
            Circle()
                .fill(Color.lemonade)
                .frame(width: 22, height: 22)
            ForEach(0..<4, id: \.self) { index in
                Capsule()
                    .fill(Color.lemonSlice)
                    .frame(width: 2, height: 22)
                    .rotationEffect(.degrees(Double(index) * 45))
            }
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    LunchIllustrationView()
        .padding()
}
#endif
