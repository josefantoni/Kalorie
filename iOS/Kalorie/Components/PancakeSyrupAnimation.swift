//
//  PancakeSyrupAnimation.swift
//  Kalorie
//
//  Created by Josef Antoni on 02.11.2026.
//

import SwiftUI

// swiftlint:disable identifier_name multiline_arguments multiline_arguments_brackets multiline_parameters multiline_parameters_brackets multiline_literal_brackets

// MARK: - View

struct PancakeSyrupAnimation: View {

    // MARK: - Properties

    @State private var start = Date()

    // MARK: - Body

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = min(max(0, timeline.date.timeIntervalSince(start)), PancakeScene.loop)
            Canvas { context, size in
                PancakeScene.draw(context, size: size, t: t)
            }
            .opacity(PancakeScene.fade(at: t))
        }
        .aspectRatio(1, contentMode: .fit)
        .clipped()
        .accessibilityHidden(true)
    }
}

// MARK: - Colours

private extension Color {
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }
}

private enum Pal {
    static let syrup = Color(hex: 0x8C4210)
    static let syrupHi = Color(hex: 0xC9772A)
    static let panTop = Color(hex: 0xE3A444)
    static let panSide = Color(hex: 0xECC983)
    static let panLine = Color(hex: 0xA2641C)
    static let panSpot = Color(hex: 0xCF8C2E)
    static let panDot = Color(hex: 0xA8691F)
    static let plateTop = Color(hex: 0xFFFFFF)
    static let plateBack = Color(hex: 0xE9E6DC)
    static let plateLine = Color(hex: 0xC9C4B6)
    static let plateWell = Color(hex: 0xDCD8CC)
    static let shadow = Color(hex: 0xD6D1C3)
    static let bottle = Color(hex: 0xE08A2B)
    static let bottleLine = Color(hex: 0x8A4B0D)
    static let bottleHi = Color(hex: 0xF5B862)
    static let bottleMouth = Color(hex: 0x5A2A08)
    static let bottleCollar = Color(hex: 0x7A3D0C)
    static let label = Color(hex: 0xF7ECD2)
    static let labelDot = Color(hex: 0xB3361A)
}

// MARK: - Easing and keyframes

private struct Spline {
    let x1, y1, x2, y2: Double

    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        self.x1 = x1; self.y1 = y1; self.x2 = x2; self.y2 = y2
    }

    static let linear = Spline(0, 0, 1, 1)

    func ease(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        if x1 == y1 && x2 == y2 { return x }
        var lo = 0.0, hi = 1.0
        for _ in 0..<32 {
            let mid = (lo + hi) / 2
            if bx(mid) < x { lo = mid } else { hi = mid }
        }
        return by((lo + hi) / 2)
    }

    private func bx(_ s: Double) -> Double {
        let u = 1 - s
        return 3 * u * u * s * x1 + 3 * u * s * s * x2 + s * s * s
    }

    private func by(_ s: Double) -> Double {
        let u = 1 - s
        return 3 * u * u * s * y1 + 3 * u * s * s * y2 + s * s * s
    }
}

private struct Track {
    let keys: [Double]
    let values: [Double]
    let splines: [Spline]?

    init(_ keys: [Double], _ values: [Double], _ splines: [Spline]? = nil) {
        precondition(keys.count == values.count)
        self.keys = keys; self.values = values; self.splines = splines
    }

    func value(_ t: Double) -> Double {
        if t <= keys[0] { return values[0] }
        if t >= keys[keys.count - 1] { return values[values.count - 1] }
        var i = 0
        while i < keys.count - 2 && t >= keys[i + 1] { i += 1 }
        let k0 = keys[i], k1 = keys[i + 1]
        let p = k1 > k0 ? (t - k0) / (k1 - k0) : 1
        let e = splines?[i].ease(p) ?? p
        return values[i] + (values[i + 1] - values[i]) * e
    }
}

// MARK: - Helper shapes

private func ellipse(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
    Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: 2 * rx, height: 2 * ry))
}

private func polygon(_ pts: [CGPoint]) -> Path {
    var p = Path()
    guard let first = pts.first else { return p }
    p.move(to: first)
    for q in pts.dropFirst() { p.addLine(to: q) }
    p.closeSubpath()
    return p
}

private func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

// MARK: - Scene

enum PancakeScene {
    static let loop: Double = 9

    private static let fadeTrack = Track([0, 7.5, 8.4, 9], [1, 1, 0, 0])

    static func fade(at t: Double) -> Double { fadeTrack.value(t) }

    // MARK: Timelines

    private static let plateX = Track([0, 0.6, 9], [420, 0, 0],
                                      [Spline(0.2, 0.7, 0.2, 1), .linear])

    private static func dropTrack(start ts: Double, land tl: Double) -> Track {
        Track([0, ts, tl, tl + 0.06, tl + 0.12, 9],
              [-330, -330, 0, -5, 0, 0],
              [.linear, Spline(0.5, 0, 1, 1), Spline(0, 0.4, 0.4, 1), Spline(0.6, 0, 1, 1), .linear])
    }
    private static let dropBottom = dropTrack(start: 0.5, land: 1.0)
    private static let dropTop = dropTrack(start: 0.95, land: 1.45)

    private static let shadowAlpha = Track([0, 0.5, 1.0, 9], [0, 0, 0.85, 0.85])

    private static let poolScale = Track([0, 2.25, 2.75, 3.2, 3.6, 8.91, 9],
                                         [0, 0, 0.2, 1.05, 1, 1, 0])

    private static let bottleKeys: [Double] = [0, 1.4, 1.8, 2.05, 2.2, 3.3, 4.0, 9]
    private static let bottleSplines: [Spline] = [
        .linear, Spline(0.3, 0, 0.2, 1), Spline(0.5, 0, 0.3, 1), Spline(0.3, 0, 0.3, 1),
        .linear, Spline(0.3, 0, 0.2, 1), .linear
    ]
    private static let bottleX = Track(bottleKeys, [480, 480, 250, 250, 250, 250, 480, 480], bottleSplines)
    private static let bottleY = Track(bottleKeys, [0, 0, 130, 130, 130, 130, 0, 0], bottleSplines)
    private static let bottleRot = Track(bottleKeys, [0, 0, 12, -140, -130, -130, 0, 0], bottleSplines)

    private static let dashAlpha = Track([0, 2.15, 2.25, 2.45, 2.55, 2.65, 2.8, 9],
                                         [0, 0, 1, 1, 0, 1, 0, 0])
    private static let splashAlpha = Track([0, 2.75, 2.85, 3.3, 3.4, 9], [0, 0, 1, 1, 0, 0])

    private static let biteCuts: [Double] = [4.5, 5.3, 6.1]
    private static let biteX: [Double] = [240, 165, 0]
    private static let scallops: [(y: Double, r: Double)] = [(262, 24), (298, 22)]

    // MARK: Pancake geometry

    private struct PancakeSpec {
        let cx, cy, rx, ry: Double
        let path: Path
    }

    private static func outline(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double,
                                _ phase: Double) -> Path {
        let n = 72
        var pts: [CGPoint] = []
        for i in 0..<n {
            let th = 2 * Double.pi * Double(i) / Double(n)
            let rf = 1 + 0.018 * sin(3 * th + phase) + 0.012 * sin(5 * th - 2 * phase)
            pts.append(CGPoint(x: cx + rx * rf * cos(th), y: cy + ry * rf * sin(th)))
        }
        return polygon(pts)
    }

    private static let bottomCake = PancakeSpec(cx: 200, cy: 283, rx: 106, ry: 24,
                                                path: outline(200, 283, 106, 24, 0.6))
    private static let topCake = PancakeSpec(cx: 192, cy: 271, rx: 94, ry: 21,
                                             path: outline(192, 271, 94, 21, 2.1))
    private static let cakeThickness = 5

    private static let mottles: [(cx: Double, cy: Double, rx: Double, ry: Double)] = [
        (-9.0, 2.4, 10, 3.2), (80.6, -1.4, 15, 3.2), (-59.9, 0.5, 12, 3.2),
        (24.7, 11.9, 17, 3.2), (-77.1, -8.0, 14, 3.2), (29.2, 4.7, 11, 3.2),
        (5.4, -17.8, 16, 3.2), (-58.9, -10.4, 13, 3.2), (-11.3, 13.9, 10, 3.2)
    ]
    private static let dots: [(cx: Double, cy: Double, r: Double)] = [
        (3.6, 5.7, 1.50), (-8.1, -9.0, 1.85), (64.6, 8.4, 2.20), (-35.1, -10.9, 1.50),
        (50.6, -4.0, 1.85), (65.9, -4.6, 2.20), (-81.1, 5.2, 1.50), (52.9, -9.3, 1.85),
        (56.4, -13.0, 2.20), (11.3, -2.1, 1.50), (-58.8, 9.4, 1.85), (-72.9, -3.2, 2.20),
        (-37.2, 15.6, 1.50), (-55.0, -4.3, 1.85), (-48.8, 4.1, 2.20), (-24.4, -1.9, 1.50),
        (14.2, 14.8, 1.85), (-47.6, -12.6, 2.20), (72.6, 4.2, 1.50), (-14.9, -16.0, 1.85),
        (-46.2, 13.1, 2.20), (18.3, -8.4, 1.50), (13.7, -14.9, 1.85), (-3.4, 9.3, 2.20),
        (-80.7, 1.9, 1.50), (45.0, 8.2, 1.85), (-28.2, 7.5, 2.20)
    ]

    // MARK: Stream path

    private static let p0 = CGPoint(x: 250, y: 130)
    private static let p1 = CGPoint(x: 237, y: 141)
    private static let p2 = CGPoint(x: 208, y: 205)
    private static let p3 = CGPoint(x: 199, y: 272)

    private static func bez(_ s: Double) -> (x: Double, y: Double) {
        let u = 1 - s
        let a = u * u * u, b = 3 * u * u * s, c = 3 * u * s * s, d = s * s * s
        return (a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                a * p0.y + b * p1.y + c * p2.y + d * p3.y)
    }

    private static func dbez(_ s: Double) -> (x: Double, y: Double) {
        let u = 1 - s
        return (3 * u * u * (p1.x - p0.x) + 6 * u * s * (p2.x - p1.x) + 3 * s * s * (p3.x - p2.x),
                3 * u * u * (p1.y - p0.y) + 6 * u * s * (p2.y - p1.y) + 3 * s * s * (p3.y - p2.y))
    }

    private static let arcTable: (pts: [CGPoint], cum: [Double]) = {
        let n = 1000
        var pts: [CGPoint] = []
        var cum: [Double] = [0]
        for i in 0...n {
            let b = bez(Double(i) / Double(n))
            pts.append(CGPoint(x: b.x, y: b.y))
            if i > 0 {
                cum.append(cum[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y))
            }
        }
        return (pts, cum)
    }()

    private static func pointAtArc(_ f: Double) -> CGPoint {
        let cum = arcTable.cum, pts = arcTable.pts
        let target = clamp01(f) * cum[cum.count - 1]
        var lo = 0, hi = cum.count - 1
        while lo < hi {
            let mid = (lo + hi) / 2
            if cum[mid] < target { lo = mid + 1 } else { hi = mid }
        }
        if lo == 0 { return pts[0] }
        let seg = cum[lo] - cum[lo - 1]
        let q = seg > 0 ? (target - cum[lo - 1]) / seg : 0
        return CGPoint(x: pts[lo - 1].x + (pts[lo].x - pts[lo - 1].x) * q,
                       y: pts[lo - 1].y + (pts[lo].y - pts[lo - 1].y) * q)
    }

    private static let arcY: [Double] = [130.0, 142.13, 155.56, 169.44, 183.6, 197.94,
                                         212.5, 227.19, 242.03, 256.95, 272.0]

    private static func arcYLerp(_ f: Double) -> Double {
        let x = clamp01(f) * 10
        let i = min(9, Int(x))
        return arcY[i] + (arcY[i + 1] - arcY[i]) * (x - Double(i))
    }

    // MARK: Drawing

    static func draw(_ ctx: GraphicsContext, size: CGSize, t: Double) {
        var g = ctx
        let s = min(size.width, size.height) / 400
        g.translateBy(x: (size.width - 400 * s) / 2, y: (size.height - 400 * s) / 2)
        g.scaleBy(x: s, y: s)

        drawPlate(g, t)

        var food = g
        for (k, cut) in biteCuts.enumerated() where t >= cut {
            let xb = biteX[k]
            food.clip(to: Path(CGRect(x: xb, y: 225, width: 440 - xb, height: 130)), options: .inverse)
            if k < 2 {
                for sc in scallops {
                    food.clip(to: ellipse(xb, sc.y, sc.r, sc.r), options: .inverse)
                }
            }
        }
        drawFood(food, t)

        drawCrumbs(g, t)
        drawPour(g, t)
        drawBottle(g, t)
    }

    // MARK: Plate

    private static func drawPlate(_ g: GraphicsContext, _ t: Double) {
        var c = g
        c.translateBy(x: plateX.value(t), y: 0)
        let back = ellipse(200, 314, 150, 36)
        c.fill(back, with: .color(Pal.plateBack))
        c.stroke(back, with: .color(Pal.plateLine), lineWidth: 3.5)
        let top = ellipse(200, 304, 150, 36)
        c.fill(top, with: .color(Pal.plateTop))
        c.stroke(top, with: .color(Pal.plateLine), lineWidth: 3.5)
        c.stroke(ellipse(200, 304, 124, 28), with: .color(Pal.plateWell), lineWidth: 3)
    }

    // MARK: Pancakes and syrup

    private static func drawFood(_ g: GraphicsContext, _ t: Double) {
        let sa = shadowAlpha.value(t)
        if sa > 0 {
            g.fill(ellipse(202, 292, 112, 27), with: .color(Pal.shadow.opacity(sa)))
        }
        drawPancake(g, bottomCake, dropY: dropBottom.value(t))
        drawPancake(g, topCake, dropY: dropTop.value(t))
        drawSyrup(g, t)
    }

    private static func drawPancake(_ g: GraphicsContext, _ cake: PancakeSpec, dropY: Double) {
        var c = g
        c.translateBy(x: 0, y: dropY)

        let outlineStyle = StrokeStyle(lineWidth: 4.5, lineJoin: .round)
        for k in stride(from: cakeThickness, through: 0, by: -1) {
            var l = c
            l.translateBy(x: 0, y: CGFloat(k))
            l.stroke(cake.path, with: .color(Pal.panLine), style: outlineStyle)
        }
        for k in stride(from: cakeThickness, through: 0, by: -1) {
            var l = c
            l.translateBy(x: 0, y: CGFloat(k))
            l.fill(cake.path, with: .color(Pal.panSide))
        }
        c.fill(cake.path, with: .color(Pal.panTop))
        c.stroke(cake.path, with: .color(Pal.panLine),
                 style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))

        var sp = c
        sp.clip(to: cake.path)
        sp.translateBy(x: cake.cx, y: cake.cy)
        sp.scaleBy(x: cake.rx / 100, y: cake.ry / 22)
        for m in mottles {
            sp.fill(ellipse(m.cx, m.cy, m.rx, m.ry), with: .color(Pal.panSpot.opacity(0.8)))
        }
        for d in dots {
            sp.fill(ellipse(d.cx, d.cy, d.r, d.r), with: .color(Pal.panDot))
        }
    }

    private static let topClipPath = ellipse(192, 271, 88, 17)
    private static let poolHighlight: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -30, y: -2))
        p.addQuadCurve(to: CGPoint(x: 8, y: -5), control: CGPoint(x: -12, y: -7))
        return p
    }()

    private static func poolBlob(_ phase: Double) -> Path {
        let n = 56
        var pts: [CGPoint] = []
        for i in 0..<n {
            let th = 2 * Double.pi * Double(i) / Double(n)
            let rf = 1 + 0.035 * sin(3 * th + 2 * Double.pi * phase)
                       + 0.025 * sin(5 * th - 4 * Double.pi * phase)
            pts.append(CGPoint(x: 62 * rf * cos(th), y: 11 * rf * sin(th)))
        }
        return polygon(pts)
    }

    private static let ripples: [(a: Double, b: Double)] = [(2.8, 3.2), (3.0, 3.4), (3.2, 3.6)]

    private static let drips: [(x: Double, ts: Double, te: Double, len: Double)] = [
        (165, 3.0, 3.9, 20), (200, 3.1, 3.7, 14), (232, 3.1, 4.0, 24)
    ]
    private static let dripEase = Spline(0.35, 0, 0.65, 1)

    private static func drawSyrup(_ g: GraphicsContext, _ t: Double) {
        var c = g
        c.clip(to: topClipPath)

        let ps = poolScale.value(t)
        if ps > 0.001 {
            var pc = c
            pc.translateBy(x: 198, y: 273)
            pc.scaleBy(x: ps, y: ps)
            pc.fill(poolBlob((t.truncatingRemainder(dividingBy: 1.4)) / 1.4), with: .color(Pal.syrup))
            pc.stroke(poolHighlight, with: .color(Pal.syrupHi),
                      style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
        for r in ripples where t > r.a && t < r.b {
            let q = (t - r.a) / (r.b - r.a)
            c.stroke(ellipse(199, 272, 34 * q, 6.5 * q),
                     with: .color(Pal.syrupHi.opacity(0.7 * (1 - q))), lineWidth: 2)
        }

        for d in drips where t >= d.ts {
            let u = (d.x - 198) / 62
            let y0 = 273 + 11 * (max(0, 1 - u * u)).squareRoot() - 2
            let q = dripEase.ease(clamp01((t - d.ts) / (d.te - d.ts)))
            let len = d.len * q
            let r = 5 * clamp01((t - d.ts) / 0.1)
            g.fill(Path(CGRect(x: d.x - 4, y: y0, width: 8, height: max(len, 0.001))),
                   with: .color(Pal.syrup))
            g.fill(ellipse(d.x, y0 + len, r, r), with: .color(Pal.syrup))
        }
    }

    // MARK: Crumbs

    private static let crumbPoints: [CGPoint] = [
        CGPoint(x: -3, y: -1), CGPoint(x: -1, y: -3), CGPoint(x: 2, y: -2),
        CGPoint(x: 3, y: 1), CGPoint(x: 0, y: 3), CGPoint(x: -3, y: 2)
    ]
    private static let crumbs: [(x: Double, y: Double, size: Double, bite: Int, spot: Bool)] = [
        (268, 307, 1.2, 0, false), (287, 314, 0.9, 0, true),
        (212, 311, 1.1, 1, false), (188, 318, 0.8, 1, true),
        (150, 309, 1.0, 2, false), (124, 315, 1.2, 2, true), (162, 322, 0.8, 2, false)
    ]

    private static func drawCrumbs(_ g: GraphicsContext, _ t: Double) {
        for cr in crumbs {
            let tin = biteCuts[cr.bite] + 0.08
            let fin = cr.size * 1.35
            let sc = Track([0, tin, tin + 0.08, tin + 0.16, 9], [0, 0, fin * 1.3, fin, fin]).value(t)
            if sc <= 0.001 { continue }
            var c = g
            c.translateBy(x: cr.x, y: cr.y)
            c.scaleBy(x: sc, y: sc)
            let p = polygon(crumbPoints)
            c.fill(p, with: .color(cr.spot ? Pal.panSpot : Pal.panTop))
            c.stroke(p, with: .color(Pal.panLine), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        }
    }

    // MARK: Syrup stream, drops, splashes

    private static let fall0 = 2.35, fall1 = 2.75
    private static let ret0 = 3.3, ret1 = 3.5
    private static let headStart = 2.35 + 0.0005 * 9

    private static func streamPath(_ phase: Double) -> Path {
        let n = 44
        var left: [CGPoint] = [], right: [CGPoint] = []
        let b = 0.12 + 0.76 * phase
        let amp = sin(Double.pi * phase)
        for i in 0...n {
            let s = Double(i) / Double(n)
            var (cx, cy) = bez(s)
            let (dx, dy) = dbez(s)
            let ln = hypot(dx, dy)
            let nx = -dy / ln, ny = dx / ln
            var w = 15 - 5 * pow(s, 0.8)
            w += 1.0 * sin(2 * Double.pi * (1.6 * s - phase))
            w += 0.6 * sin(2 * Double.pi * (3.1 * s - 2 * phase))
            w += 2.6 * amp * exp(-((s - b) * (s - b)) / 0.006)
            let sway = 0.9 * s * sin(2 * Double.pi * (1.1 * s - phase))
            cx += nx * sway
            cy += ny * sway
            left.append(CGPoint(x: cx + nx * w / 2, y: cy + ny * w / 2))
            right.append(CGPoint(x: cx - nx * w / 2, y: cy - ny * w / 2))
        }
        return polygon(left + right.reversed())
    }

    private static let fallEase = Spline(0.55, 0, 1, 0.9)
    private static let fallingDrops: [(ts: Double, dur: Double, r: Double)] = [
        (2.0, 0.3, 5), (2.175, 0.275, 4.2)
    ]

    private static let splashDrops: [(dx: Double, h: Double, r: Double, dur: Double, begin: Double)] = [
        (-22, 10, 3, 0.6, 0), (-10, 14, 3.6, 0.55, 0.15), (10, 14, 3.6, 0.55, 0.08), (22, 10, 3, 0.6, 0.25)
    ]

    private static func drawPour(_ g: GraphicsContext, _ t: Double) {
        let syrupColor = Pal.syrup

        if t >= fall0 && t < ret1 {
            let bottom = t < fall1 ? arcYLerp((t - fall0) / (fall1 - fall0)) : 272.0
            let top = t < ret0 ? 0.0 : arcYLerp((t - ret0) / (ret1 - ret0))
            if bottom - top > 0.01 {
                var c = g
                c.clip(to: Path(CGRect(x: 0, y: top, width: 400, height: bottom - top)))
                c.fill(streamPath((t.truncatingRemainder(dividingBy: 0.9)) / 0.9), with: .color(syrupColor))
            }
        }

        if t >= fall0 && t < fall1 + 0.025 {
            let f = clamp01((t - headStart) / (fall1 - headStart))
            let pos = pointAtArc(f)
            let r = 9 + (6.5 - 9) * f
            let a: Double
            if t < fall0 + 0.01 { a = (t - fall0) / 0.01 } else if t <= fall1 { a = 1 } else { a = 1 - (t - fall1) / 0.025 }
            g.fill(ellipse(pos.x, pos.y, r, r), with: .color(syrupColor.opacity(clamp01(a))))
        }

        for d in fallingDrops {
            let te = d.ts + d.dur
            if t < d.ts || t > te + 0.02 { continue }
            let e = Track([0, d.ts, te, 9], [0, 0, 1, 1], [.linear, fallEase, .linear]).value(t)
            let pos = pointAtArc(e)
            let a = t <= te ? 1.0 : 1 - (t - te) / 0.02
            g.fill(ellipse(pos.x, pos.y, d.r, d.r), with: .color(syrupColor.opacity(clamp01(a))))
        }

        let sa = splashAlpha.value(t)
        if sa > 0.001 {
            var c = g
            c.clip(to: topClipPath)
            for d in splashDrops {
                let local = t - d.begin
                if local < 0 { continue }
                let p = local.truncatingRemainder(dividingBy: d.dur) / d.dur
                let x4 = p * 4
                let i = min(3, Int(x4))
                let f = x4 - Double(i)
                func sample(_ k: Int) -> (Double, Double) {
                    let q = Double(k) / 4
                    return (d.dx * q, -4 * d.h * q * (1 - q))
                }
                let a0 = sample(i), a1 = sample(i + 1)
                let ox = a0.0 + (a1.0 - a0.0) * f
                let oy = a0.1 + (a1.1 - a0.1) * f
                let alpha = p < 0.25 ? p * 4 : (p <= 0.75 ? 1 : (1 - p) * 4)
                c.fill(ellipse(199 + ox, 270 + oy, d.r, d.r),
                       with: .color(syrupColor.opacity(clamp01(alpha) * sa)))
            }
        }

        let da = dashAlpha.value(t)
        if da > 0.001 {
            var dash = Path()
            dash.move(to: CGPoint(x: 237, y: 124)); dash.addLine(to: CGPoint(x: 230, y: 119))
            dash.move(to: CGPoint(x: 243, y: 115)); dash.addLine(to: CGPoint(x: 240, y: 108))
            dash.move(to: CGPoint(x: 232, y: 134)); dash.addLine(to: CGPoint(x: 224, y: 133))
            g.stroke(dash, with: .color(Pal.syrupHi.opacity(da)),
                     style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
    }

    // MARK: Bottle

    private static let bottleBody: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -9, y: 0))
        p.addLine(to: CGPoint(x: 9, y: 0))
        p.addLine(to: CGPoint(x: 9, y: 24))
        p.addCurve(to: CGPoint(x: 32, y: 62), control1: CGPoint(x: 9, y: 34), control2: CGPoint(x: 32, y: 38))
        p.addLine(to: CGPoint(x: 32, y: 106))
        p.addQuadCurve(to: CGPoint(x: 18, y: 120), control: CGPoint(x: 32, y: 120))
        p.addLine(to: CGPoint(x: -18, y: 120))
        p.addQuadCurve(to: CGPoint(x: -32, y: 106), control: CGPoint(x: -32, y: 120))
        p.addLine(to: CGPoint(x: -32, y: 62))
        p.addCurve(to: CGPoint(x: -9, y: 24), control1: CGPoint(x: -32, y: 38), control2: CGPoint(x: -9, y: 34))
        p.closeSubpath()
        return p
    }()

    private static func drawBottle(_ g: GraphicsContext, _ t: Double) {
        let x = bottleX.value(t)
        if x >= 470 { return }
        var c = g
        c.translateBy(x: x, y: bottleY.value(t))
        c.rotate(by: .degrees(bottleRot.value(t)))

        c.fill(bottleBody, with: .color(Pal.bottle))
        c.stroke(bottleBody, with: .color(Pal.bottleLine),
                 style: StrokeStyle(lineWidth: 3, lineJoin: .round))

        var hi = Path()
        hi.move(to: CGPoint(x: -23, y: 70))
        hi.addLine(to: CGPoint(x: -23, y: 104))
        c.stroke(hi, with: .color(Pal.bottleHi), style: StrokeStyle(lineWidth: 5, lineCap: .round))

        c.fill(ellipse(0, 0, 9, 2.6), with: .color(Pal.bottleMouth))
        c.fill(Path(roundedRect: CGRect(x: -12, y: 20, width: 24, height: 6), cornerRadius: 3),
               with: .color(Pal.bottleCollar))

        let label = Path(roundedRect: CGRect(x: -20, y: 66, width: 40, height: 36), cornerRadius: 7)
        c.fill(label, with: .color(Pal.label))
        c.stroke(label, with: .color(Pal.bottleLine), lineWidth: 2.5)
        c.fill(ellipse(0, 84, 8, 8), with: .color(Pal.labelDot))
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    PancakeSyrupAnimation()
        .frame(width: 300, height: 300)
        .padding()
        .background(Color.accentColor)
}
#endif
