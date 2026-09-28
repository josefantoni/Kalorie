//
//  SignInSpotlightView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.09.2026.
//

import SwiftUI

struct SignInSpotlightView: View {

    // MARK: - Properties

    let targetFrame: CGRect
    let onSignIn: () -> Void
    let onDismiss: () -> Void

    @AccessibilityFocusState private var isBubbleFocused: Bool

    private let cutoutPadding: CGFloat = 6
    private let bubbleCornerRadius: CGFloat = 16
    private let arrowSize = CGSize(width: 20, height: 10)
    private let bubbleMaxWidth: CGFloat = 300
    private let screenMargin: CGFloat = 8
    private let autoDismissSeconds = 10

    // MARK: - Body

    var body: some View {
        GeometryReader { proxy in
            let origin = proxy.frame(in: .global).origin
            let cutout = cutoutRect(for: targetFrame.offsetBy(dx: -origin.x, dy: -origin.y))
            ZStack(alignment: .topLeading) {
                dimmedBackground(size: proxy.size, cutout: cutout)
                bubble(screenWidth: proxy.size.width, cutout: cutout)
            }
        }
        .ignoresSafeArea()
        .accessibilityAddTraits(.isModal)
        .task {
            isBubbleFocused = true
            guard !UIAccessibility.isVoiceOverRunning else { return }
            try? await Task.sleep(for: .seconds(autoDismissSeconds))
            guard !Task.isCancelled else { return }
            onDismiss()
        }
    }

    // MARK: - Functions

    private func cutoutRect(for frame: CGRect) -> CGRect {
        let diameter = max(frame.width, frame.height) + 2 * cutoutPadding
        return CGRect(x: frame.midX - diameter / 2, y: frame.midY - diameter / 2, width: diameter, height: diameter)
    }

    private func dimmedBackground(size: CGSize, cutout: CGRect) -> some View {
        Path { path in
            path.addRect(CGRect(origin: .zero, size: size))
            path.addEllipse(in: cutout)
        }
        .fill(Color.black.opacity(0.4), style: FillStyle(eoFill: true))
        .contentShape(Rectangle())
        .gesture(
            SpatialTapGesture().onEnded { value in
                if cutout.contains(value.location) {
                    onSignIn()
                } else {
                    onDismiss()
                }
            }
        )
        .accessibilityHidden(true)
    }

    private func bubble(screenWidth: CGFloat, cutout: CGRect) -> some View {
        let width = min(bubbleMaxWidth, screenWidth - 2 * screenMargin)
        let idealBubbleX = cutout.midX - arrowSize.width / 2 - bubbleCornerRadius
        let bubbleX = min(max(idealBubbleX, screenMargin), screenWidth - width - screenMargin)
        let arrowX = min(max(cutout.midX - bubbleX - arrowSize.width / 2, 0), width - arrowSize.width)
        return VStack(alignment: .leading, spacing: -2) {
            Triangle()
                .fill(Color(uiColor: .systemBackground))
                .frame(width: arrowSize.width, height: arrowSize.height)
                .padding(.leading, arrowX)
            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.Dashboard.signInSpotlightTitle)
                    .font(.headline)
                Text(L10n.Dashboard.signInSpotlightMessage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
            .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: bubbleCornerRadius))
        }
        .frame(width: width)
        .offset(x: bubbleX, y: cutout.maxY + 4)
        .allowsHitTesting(false)
        .accessibilityElement(children: .combine)
        .accessibilityFocused($isBubbleFocused)
        .accessibilityAction(named: L10n.Dashboard.signInSpotlightActionSignIn, onSignIn)
        .accessibilityAction(.escape, onDismiss)
    }
}

// MARK: - Triangle

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        Path { path in
            path.move(to: CGPoint(x: rect.midX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            path.closeSubpath()
        }
    }
}
