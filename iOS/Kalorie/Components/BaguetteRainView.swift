//
//  BaguetteRainView.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import SwiftUI

struct BaguetteDrop: Identifiable {

    // MARK: - Properties

    let id: Int
    let delay: Double
    let fallDuration: Double
    let cycle: Double

    // MARK: - Functions

    static func makeDrops(duration: Double, count: Int) -> [BaguetteDrop] {
        (0..<count).map { index in
            BaguetteDrop(
                id: index,
                delay: Double(index) / Double(count) * duration,
                fallDuration: .random(in: 1.1...1.6),
                cycle: duration
            )
        }
    }

    func xFraction(fall: Int) -> CGFloat { 0.02 + 0.96 * unit(fall: fall, salt: 1) }
    func length(fall: Int) -> CGFloat { 70 + 50 * unit(fall: fall, salt: 2) }
    func startAngle(fall: Int) -> Double { -60 + 120 * unit(fall: fall, salt: 3) }
    func endAngle(fall: Int) -> Double { -240 + 480 * unit(fall: fall, salt: 4) }

    private func unit(fall: Int, salt: UInt64) -> Double {
        var value = UInt64(id) &* 1_000_003 &+ UInt64(fall) &* 7_919 &+ salt &+ 0x9E37_79B9_7F4A_7C15
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        value ^= value >> 31
        return Double(value >> 11) / Double(1 << 53)
    }
}

struct BaguetteRainScene: View {

    // MARK: - Properties

    let drops: [BaguetteDrop]
    let elapsed: Double

    // MARK: - Body

    var body: some View {
        GeometryReader { proxy in
            ForEach(drops) { drop in
                let sinceDelay = elapsed - drop.delay
                let fall = Int(max(sinceDelay, 0) / drop.cycle)
                let progress = (sinceDelay - Double(fall) * drop.cycle) / drop.fallDuration
                if sinceDelay >= 0 && progress <= 1 {
                    let length = drop.length(fall: fall)
                    let startAngle = drop.startAngle(fall: fall)
                    BaguetteView(length: length)
                        .rotationEffect(.degrees(startAngle + (drop.endAngle(fall: fall) - startAngle) * progress))
                        .position(
                            x: proxy.size.width * drop.xFraction(fall: fall),
                            y: -length + (proxy.size.height + 2 * length) * progress
                        )
                }
            }
        }
        .allowsHitTesting(false)
    }
}

struct BaguetteRainView: View {

    // MARK: - Properties

    private let drops: [BaguetteDrop]
    @State private var startDate = Date.now

    // MARK: - Init

    init(duration: Double = 5, count: Int = 40) {
        drops = BaguetteDrop.makeDrops(duration: duration, count: count)
    }

    // MARK: - Body

    var body: some View {
        TimelineView(.animation) { context in
            BaguetteRainScene(drops: drops, elapsed: context.date.timeIntervalSince(startDate))
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    BaguetteRainView()
}
#endif
