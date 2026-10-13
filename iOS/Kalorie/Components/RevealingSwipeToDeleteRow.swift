//
//  RevealingSwipeToDeleteRow.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.10.2026.
//

import SwiftUI

struct RevealingSwipeToDeleteRow<Content: View>: View {

    // MARK: - Properties

    let id: String
    @Binding var openRowId: String?
    let onDelete: () -> Void
    @ViewBuilder let content: Content

    @State private var offset: CGFloat = 0
    @State private var restingOffset: CGFloat = 0
    @State private var rowWidth: CGFloat = 0

    private let actionWidth: CGFloat = 80

    // MARK: - Init

    init(
        id: String,
        openRowId: Binding<String?>,
        onDelete: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.id = id
        self._openRowId = openRowId
        self.onDelete = onDelete
        self.content = content()
    }

    // MARK: - Body

    var body: some View {
        ZStack(alignment: .trailing) {
            Button {
                close()
                onDelete()
            } label: {
                Image(systemName: BaseImageName.trash.rawValue)
                    .foregroundStyle(.white)
                    .frame(width: max(actionWidth, -offset))
                    .frame(maxHeight: .infinity)
                    .background(Color.error)
            }
            .opacity(offset < 0 ? 1 : 0)
            .accessibilityHidden(true)

            content
                .background(Color(.secondarySystemGroupedBackground))
                .offset(x: offset)
                .accessibilityAction(named: L10n.Common.buttonDelete) {
                    onDelete()
                }
                .gesture(
                    HorizontalPanGesture { translation in
                        if openRowId != id { openRowId = nil }
                        offset = min(0, restingOffset + translation)
                    } onEnded: { translation, velocity in
                        onSwipeEnded(translation: translation, velocity: velocity)
                    }
                )
        }
        .clipped()
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidth = $0 }
        .onChange(of: openRowId) { _, newValue in
            if newValue != id && restingOffset != 0 { close() }
        }
    }

    // MARK: - Functions

    private func onSwipeEnded(translation: CGFloat, velocity: CGFloat) {
        let finalOffset = min(0, restingOffset + translation)
        let isFullSwipe = finalOffset < -rowWidth * 0.5 || (velocity < -1_000 && finalOffset < -actionWidth)
        if isFullSwipe {
            close()
            onDelete()
        } else {
            withAnimation(.easeOut(duration: 0.2)) {
                restingOffset = finalOffset < -actionWidth / 2 ? -actionWidth : 0
                offset = restingOffset
            }
            openRowId = restingOffset == 0 ? nil : id
        }
    }

    private func close() {
        if openRowId == id { openRowId = nil }
        withAnimation(.easeOut(duration: 0.2)) {
            restingOffset = 0
            offset = 0
        }
    }
}

// MARK: - HorizontalPanGesture

private struct HorizontalPanGesture: UIGestureRecognizerRepresentable {

    // MARK: - Properties

    let onChanged: (CGFloat) -> Void
    let onEnded: (CGFloat, CGFloat) -> Void

    // MARK: - Functions

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let recognizer = UIPanGestureRecognizer()
        recognizer.delegate = context.coordinator
        return recognizer
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        let translation = recognizer.translation(in: recognizer.view).x
        switch recognizer.state {
        case .changed:
            onChanged(translation)
        case .ended:
            onEnded(translation, recognizer.velocity(in: recognizer.view).x)
        case .cancelled, .failed:
            onEnded(0, 0)
        default:
            break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        // Without this the pan would also begin for vertical drags and steal them from the ScrollView.
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            return abs(velocity.x) > abs(velocity.y)
        }
    }
}
