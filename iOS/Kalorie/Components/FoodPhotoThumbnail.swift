//
//  FoodPhotoThumbnail.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import PhotosUI
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

enum FoodPhotoPillState: Equatable {
    case hidden
    case photo(URL)
    case empty
    case awaitingApproval
}

private struct FoodPhotoPillPlaceholder: View {

    // MARK: - Properties

    let width: CGFloat
    let isAwaiting: Bool
    var isSending = false

    // MARK: - Body

    var body: some View {
        Capsule()
            .strokeBorder(Color.secondary, style: StrokeStyle(lineWidth: 2, dash: [6]))
            .frame(width: width, height: 44)
            .overlay {
                if isSending {
                    AccentProgressView()
                } else if isAwaiting {
                    Image(systemName: BaseImageName.clock.rawValue)
                        .font(.body)
                        .foregroundStyle(Color.secondary)
                } else {
                    Image(systemName: BaseImageName.cameraFill.rawValue)
                        .font(.footnote)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .frame(width: 30, height: 30)
                        .background(Color.accentColor, in: .circle)
                }
            }
            .contentShape(.capsule)
    }
}

private struct FoodPhotoPillModifier: ViewModifier {

    // MARK: - Properties

    let state: FoodPhotoPillState
    let onSend: (Data) async -> Void
    var cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProvider()

    @State private var isViewerVisible = false
    @State private var isSourceDialogVisible = false
    @State private var isAwaitingPopoverVisible = false
    @State private var isSending = false
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

    private var photoURL: URL? {
        if case .photo(let url) = state { url } else { nil }
    }

    private static var sideReserve: CGFloat { 132 }
    private static var maxBubbleWidth: CGFloat { 320 }

    // MARK: - Body

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGFloat.self) { $0.size.width.rounded() } action: { containerWidth = $0 }
            .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).midX.rounded() } action: { containerMidX = $0 }
            .toolbar {
                if state != .hidden {
                    ToolbarItem(placement: .principal) {
                        ZStack {
                            Color.clear
                                .frame(width: 1, height: 1)
                                .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).midX.rounded() } action: { itemMidX = $0 }
                            pill
                                .offset(x: centerOffset)
                        }
                    }
                    .sharedBackgroundVisibility(.hidden)
                }
            }
            .fullScreenCover(isPresented: $isViewerVisible) {
                if let photoURL {
                    FoodPhotoViewer(photo: .remote(photoURL)) { isViewerVisible = false }
                }
            }
            .foodPhotoSource(
                isPresented: $isSourceDialogVisible,
                isProcessing: $isSending,
                cameraAuthorizationProvider: cameraAuthorizationProvider
            ) { processed in
                await onSend(processed)
            }
    }

    @ViewBuilder private var pill: some View {
        switch state {
        case .hidden:
            EmptyView()
        case .photo(let url):
            FoodPhotoThumbnail(url: url, width: bubbleWidth) { withoutAnimation { isViewerVisible = true } }
        case .empty:
            Button {
                isSourceDialogVisible = true
            } label: {
                FoodPhotoPillPlaceholder(width: bubbleWidth, isAwaiting: false, isSending: isSending)
            }
            .buttonStyle(.plain)
            .disabled(isSending)
            .accessibilityLabel(L10n.FoodPhoto.suggestionAdd)
        case .awaitingApproval:
            Button {
                isAwaitingPopoverVisible = true
            } label: {
                FoodPhotoPillPlaceholder(width: bubbleWidth, isAwaiting: true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L10n.FoodPhoto.suggestionAwaiting)
            .popover(isPresented: $isAwaitingPopoverVisible) {
                Text(L10n.FoodPhoto.suggestionAwaiting)
                    .foregroundStyle(Color(.label))
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(width: 240)
                    .padding()
                    .dynamicTypeLimit()
                    .presentationCompactAdaptation(.popover)
            }
        }
    }
}

extension View {
    func foodPhotoPill(state: FoodPhotoPillState, onSend: @escaping (Data) async -> Void) -> some View {
        modifier(FoodPhotoPillModifier(state: state, onSend: onSend))
    }
}
