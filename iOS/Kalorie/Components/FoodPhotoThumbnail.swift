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
    let onTapped: () -> Void

    // MARK: - Body

    var body: some View {
        AsyncImage(url: url) { phase in
            if let image = phase.image {
                Button(action: onTapped) {
                    ZStack(alignment: .bottomTrailing) {
                        image
                            .resizable()
                            .scaledToFill()
                            .frame(width: Self.diameter, height: Self.diameter)
                            .clipShape(.circle)
                        Image(systemName: BaseImageName.enlarge.rawValue)
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .foregroundStyle(.white)
                            .frame(width: Self.badgeDiameter, height: Self.badgeDiameter)
                            .background(Color.accentColor, in: .circle)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.FoodPhoto.accessibilityEnlarge)
                .padding(.leading, 20)
                .padding(.bottom, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else if case .empty = phase {
                ProgressView()
                    .frame(width: Self.diameter, height: Self.diameter)
                    .background(Color.secondary.opacity(0.15), in: .circle)
                    .padding(.leading, 20)
                    .padding(.bottom, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Color.clear.frame(height: 0)
            }
        }
    }

    // MARK: - Functions

    private static var diameter: CGFloat { 60 }
    private static var badgeDiameter: CGFloat { 24 }
}

private struct FoodPhotoThumbnailModifier: ViewModifier {

    // MARK: - Properties

    let url: URL?
    let isHidden: Bool
    @State private var isViewerVisible = false

    // MARK: - Body

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom) {
                if let url, !isHidden {
                    FoodPhotoThumbnail(url: url) { isViewerVisible = true }
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
    func foodPhotoThumbnail(url: URL?, isHidden: Bool = false) -> some View {
        modifier(FoodPhotoThumbnailModifier(url: url, isHidden: isHidden))
    }
}
