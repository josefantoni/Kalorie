//
//  GrandmaCookingView.swift
//  Kalorie
//
//  Created by Josef Antoni on 16.10.2026.
//

import SwiftUI

struct GrandmaCookingView: View {

    // MARK: - Body

    var body: some View {
        TimelineView(.animation) { context in
            GrandmaCookingScene(time: context.date.timeIntervalSinceReferenceDate)
        }
    }
}

struct GrandmaCookingScene: View {

    private enum Food: CaseIterable {
        case dumpling
        case schnitzel
        case heart
    }

    // MARK: - Properties

    let time: Double

    private let potMouthY: CGFloat = 40

    // MARK: - Body

    var body: some View {
        ZStack {
            grandma.offset(y: -22)
            ForEach(0..<3, id: \.self) { index in
                steamPuff(index: index)
            }
            ForEach(0..<6, id: \.self) { index in
                poppingFood(index: index)
            }
            pot.offset(y: 62)
        }
        .frame(width: 320, height: 280)
    }

    // MARK: - Private

    private var spoonAngle: Double {
        sin(time * 4) * 14
    }

    private var grandma: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 40)
                .fill(Color.grandmaDress)
                .frame(width: 124, height: 120)
                .offset(y: 54)
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.plate)
                .frame(width: 70, height: 70)
                .offset(y: 70)
            Circle()
                .fill(Color.grandmaHair)
                .frame(width: 30, height: 30)
                .offset(y: -78)
            head
            arm
        }
        .frame(width: 200, height: 220)
    }

    private var head: some View {
        ZStack {
            Circle()
                .fill(Color.grandmaSkin)
                .frame(width: 72, height: 72)
            Circle()
                .fill(Color.grandmaHair)
                .frame(width: 72, height: 72)
                .mask { Rectangle().frame(width: 72, height: 26).offset(y: -23) }
            ForEach(0..<2, id: \.self) { index in
                let eyeX: CGFloat = index == 0 ? -14 : 14
                Circle()
                    .stroke(Color.potShade, lineWidth: 2.5)
                    .frame(width: 20, height: 20)
                    .offset(x: eyeX, y: -2)
                Circle()
                    .fill(Color.grandmaEye)
                    .frame(width: 5, height: 5)
                    .offset(x: eyeX, y: -2)
                Circle()
                    .fill(Color.favourite.opacity(0.35))
                    .frame(width: 11, height: 11)
                    .offset(x: eyeX * 1.5, y: 12)
            }
            Capsule()
                .fill(Color.potShade)
                .frame(width: 8, height: 2.5)
                .offset(y: -2)
            Circle()
                .trim(from: 0.12, to: 0.38)
                .stroke(Color.grandmaMouth, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .frame(width: 24, height: 24)
                .offset(y: 6)
        }
        .frame(width: 80, height: 80)
        .offset(y: -42)
    }

    private var arm: some View {
        ZStack {
            Capsule()
                .fill(Color.baguetteScore)
                .frame(width: 8, height: 78)
                .offset(y: -34)
            Circle()
                .fill(Color.grandmaSkin)
                .frame(width: 18, height: 18)
        }
        .rotationEffect(.degrees(spoonAngle), anchor: .bottom)
        .offset(x: 34, y: 66)
        .overlay(alignment: .center) {
            Capsule()
                .fill(Color.grandmaDress)
                .frame(width: 60, height: 22)
                .rotationEffect(.degrees(-25))
                .offset(x: 14, y: 62)
        }
    }

    private var pot: some View {
        ZStack {
            Capsule()
                .fill(Color.potShade)
                .frame(width: 30, height: 12)
                .offset(x: -88, y: -18)
            Capsule()
                .fill(Color.potShade)
                .frame(width: 30, height: 12)
                .offset(x: 88, y: -18)
            RoundedRectangle(cornerRadius: 22)
                .fill(Color.potMetal)
                .frame(width: 160, height: 84)
            Capsule()
                .fill(Color.plate.opacity(0.35))
                .frame(width: 8, height: 46)
                .offset(x: -56, y: 4)
            Capsule()
                .fill(Color.potShade)
                .frame(width: 172, height: 16)
                .offset(y: -40)
        }
        .frame(width: 200, height: 100)
    }

    private func steamPuff(index: Int) -> some View {
        let period = 2.4
        let phase = ((time + Double(index) * period / 3).truncatingRemainder(dividingBy: period)) / period
        return Circle()
            .fill(Color.plate.opacity(0.85 * (1 - phase)))
            .frame(width: 22 + 26 * phase, height: 22 + 26 * phase)
            .offset(
                x: CGFloat(index - 1) * 34 + CGFloat(sin(phase * .pi * 2 + Double(index))) * 10,
                y: potMouthY + 20 - 70 * phase
            )
    }

    private func poppingFood(index: Int) -> some View {
        let period = 1.8
        let shifted = (time + Double(index) * period / 6).truncatingRemainder(dividingBy: period) / period
        let direction: CGFloat = index.isMultiple(of: 2) ? -1 : 1
        let spread = CGFloat(70 + (index % 3) * 26)
        let rise = CGFloat(4 * shifted * (1 - shifted)) * (60 + CGFloat(index % 3) * 18)
        let food = Food.allCases[index % Food.allCases.count]
        return foodView(food)
            .rotationEffect(.degrees(shifted * 240 * Double(direction)))
            .offset(x: direction * spread * CGFloat(shifted * 0.6 + 0.4), y: potMouthY + 30 - rise)
            .opacity(shifted < 0.05 ? shifted * 20 : 1)
    }

    @ViewBuilder
    private func foodView(_ food: Food) -> some View {
        switch food {
        case .dumpling:
            ZStack {
                Ellipse().fill(Color.breadCrumb).frame(width: 34, height: 22)
                Capsule().fill(Color.baguetteCrust.opacity(0.6)).frame(width: 22, height: 3).offset(y: 3)
            }
        case .schnitzel:
            ZStack {
                RoundedRectangle(cornerRadius: 10).fill(Color.baguetteCrust).frame(width: 40, height: 26)
                RoundedRectangle(cornerRadius: 8).fill(Color.chickenRoast.opacity(0.7)).frame(width: 28, height: 14)
            }
        case .heart:
            Image(systemName: "heart.fill")
                .font(.title2)
                .foregroundStyle(Color.favourite)
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    GrandmaCookingView()
        .padding()
}
#endif
