//
//  FoodPhotoPicker.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import AVFoundation
import SwiftUI

struct FoodPhotoPicker: View {

    // MARK: - Properties

    @Binding var photo: FoodItemFormPhoto
    var isMissing = false
    var cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProvider()
    var onChanged: () -> Void = {}

    @State private var isActionSheetVisible = false
    @State private var isViewerVisible = false

    // MARK: - Body

    var body: some View {
        VStack(spacing: 8) {
            Button {
                isActionSheetVisible = true
            } label: {
                circle
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L10n.FoodPhoto.title)
            .accessibilityHint(photo == .none ? L10n.FoodPhoto.actionTake : L10n.FoodPhoto.accessibilityRetake)
            Text(isMissing ? L10n.FoodPhoto.errorRequired : L10n.FoodPhoto.title)
                .font(.footnote)
                .foregroundStyle(isMissing ? Color.error : Color.secondary)
        }
        .frame(maxWidth: .infinity)
        .foodPhotoSource(
            isPresented: $isActionSheetVisible,
            showAction: photo == .none ? nil : { withoutAnimation { isViewerVisible = true } },
            cameraAuthorizationProvider: cameraAuthorizationProvider
        ) { processed in
            photo = .local(processed)
            onChanged()
        }
        .fullScreenCover(isPresented: $isViewerVisible) {
            FoodPhotoViewer(photo: photo) { isViewerVisible = false }
        }
    }

    // MARK: - Functions

    private var circle: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                if photo == .none {
                    Circle()
                        .strokeBorder(isMissing ? Color.error : Color.secondary, style: StrokeStyle(lineWidth: 2, dash: [6]))
                    Image(systemName: BaseImageName.foodPlaceholder.rawValue)
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                } else {
                    FoodPhotoContent(photo: photo)
                        .clipShape(.circle)
                }
            }
            .frame(width: Self.diameter, height: Self.diameter)
            .contentShape(.circle)
            badge
        }
    }

    private var badge: some View {
        Image(systemName: (photo == .none ? BaseImageName.cameraFill : BaseImageName.retakePhoto).rawValue)
            .font(.footnote)
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .frame(width: Self.badgeDiameter, height: Self.badgeDiameter)
            .background(Color.accentColor, in: .circle)
    }

    private static var diameter: CGFloat { 120 }
    private static var badgeDiameter: CGFloat { 36 }
}

#if DEBUG
// MARK: - Preview

#Preview {
    VStack(spacing: 24) {
        FoodPhotoPicker(photo: .constant(.none))
        FoodPhotoPicker(photo: .constant(.none), isMissing: true)
    }
}
#endif
