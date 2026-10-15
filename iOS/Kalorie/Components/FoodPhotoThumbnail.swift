//
//  FoodPhotoThumbnail.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import SwiftUI

struct FoodPhotoThumbnail: View {

    // MARK: - Properties

    let url: URL
    let width: CGFloat
    let onTapped: () -> Void

    @State private var isRevealed = false

    // MARK: - Body

    var body: some View {
        RemoteFoodImage(url: url) { phase in
            switch phase {
            case .success(let image):
                Button(action: onTapped) {
                    Color.clear
                        .frame(width: width, height: Self.height)
                        .overlay {
                            image
                                .resizable()
                                .scaledToFill()
                        }
                        .clipShape(.capsule)
                        .mask { Capsule().scaleEffect(isRevealed ? 1 : 0.01) }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.FoodPhoto.accessibilityEnlarge)
                .onAppear {
                    withAnimation(.easeOut(duration: 0.35)) { isRevealed = true }
                }
            case .loading:
                Color.clear.frame(width: width, height: Self.height)
            case .failure:
                Color.clear.frame(width: 0, height: 0)
            }
        }
    }

    // MARK: - Functions

    private static var height: CGFloat { 44 }
}

private struct FoodPhotoThumbnailModifier: ViewModifier {

    // MARK: - Properties

    let url: URL?
    @State private var isViewerVisible = false
    @State private var containerWidth: CGFloat = 0
    @State private var containerMidX: CGFloat = 0
    @State private var itemMidX: CGFloat = 0

    private var centerOffset: CGFloat {
        guard containerWidth != 0, itemMidX != 0 else { return 0 }
        return (containerMidX - itemMidX).rounded()
    }

    private var bubbleWidth: CGFloat {
        min(max(containerWidth - 2 * Self.sideReserve, 0), Self.maxBubbleWidth).rounded(.down)
    }

    private static var sideReserve: CGFloat { 132 }
    private static var maxBubbleWidth: CGFloat { 320 }

    // MARK: - Body

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGFloat.self) { $0.size.width.rounded() } action: { containerWidth = $0 }
            .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).midX.rounded() } action: { containerMidX = $0 }
            .toolbar {
                if let url {
                    ToolbarItem(placement: .principal) {
                        ZStack {
                            Color.clear
                                .frame(width: 1, height: 1)
                                .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).midX.rounded() } action: { itemMidX = $0 }
                            FoodPhotoThumbnail(url: url, width: bubbleWidth) { withoutAnimation { isViewerVisible = true } }
                                .offset(x: centerOffset)
                        }
                    }
                    .sharedBackgroundVisibility(.hidden)
                }
            }
            .fullScreenCover(isPresented: $isViewerVisible) {
                if let url {
                    FoodPhotoViewer(photo: .remote(url)) { isViewerVisible = false }
                }
            }
    }
}

extension View {
    func foodPhotoThumbnail(url: URL?) -> some View {
        modifier(FoodPhotoThumbnailModifier(url: url))
    }
}
