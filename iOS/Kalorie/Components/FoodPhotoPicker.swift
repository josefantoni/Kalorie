//
//  FoodPhotoPicker.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import AVFoundation
import PhotosUI
import SwiftUI

struct FoodPhotoPicker: View {

    // MARK: - Properties

    @Binding var photo: FoodItemFormPhoto
    var isMissing = false
    var cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProvider()
    var onChanged: () -> Void = {}

    @State private var isActionSheetVisible = false
    @State private var isLibraryVisible = false
    @State private var isCameraVisible = false
    @State private var isViewerVisible = false
    @State private var isCameraDeniedAlertVisible = false
    @State private var isProcessingFailedAlertVisible = false
    @State private var libraryItem: PhotosPickerItem?

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
        .confirmationDialog(L10n.FoodPhoto.title, isPresented: $isActionSheetVisible, titleVisibility: .hidden) {
            if photo != .none {
                Button(L10n.FoodPhoto.actionShow) { isViewerVisible = true }
            }
            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                Button(L10n.FoodPhoto.actionTake) { Task { await onTakePhotoTapped() } }
            }
            Button(L10n.FoodPhoto.actionChoose) { isLibraryVisible = true }
            Button(L10n.Common.buttonCancel, role: .cancel) {}
        }
        .photosPicker(isPresented: $isLibraryVisible, selection: $libraryItem, matching: .images)
        .onChange(of: libraryItem) { _, item in
            guard let item else { return }
            Task { await onLibraryItemPicked(item) }
        }
        .fullScreenCover(isPresented: $isCameraVisible) {
            CameraImagePicker(
                onCaptured: { data in
                    isCameraVisible = false
                    Task { await apply(data) }
                },
                onCancelled: { isCameraVisible = false }
            )
            .ignoresSafeArea()
        }
        .fullScreenCover(isPresented: $isViewerVisible) {
            FoodPhotoViewer(photo: photo) { isViewerVisible = false }
        }
        .alert(L10n.AddFood.cameraPermissionAlert, isPresented: $isCameraDeniedAlertVisible) {
            Button(L10n.AddFood.buttonOpenSettings) { openSettings() }
            Button(L10n.Common.buttonCancel, role: .cancel) {}
        }
        .alert(L10n.Common.errorUnknown, isPresented: $isProcessingFailedAlertVisible) {
            Button(L10n.Common.ok) {}
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

    @MainActor
    private func onTakePhotoTapped() async {
        switch cameraAuthorizationProvider.status {
        case .authorized, .unsupported:
            isCameraVisible = true
        case .notDetermined:
            if await cameraAuthorizationProvider.requestAccess() {
                isCameraVisible = true
            } else {
                isCameraDeniedAlertVisible = true
            }
        case .denied:
            isCameraDeniedAlertVisible = true
        }
    }

    @MainActor
    private func onLibraryItemPicked(_ item: PhotosPickerItem) async {
        defer { libraryItem = nil }
        guard let data = try? await item.loadTransferable(type: Data.self) else {
            isProcessingFailedAlertVisible = true
            return
        }
        await apply(data)
    }

    @MainActor
    private func apply(_ data: Data) async {
        do {
            let processed = try await Task.detached { try FoodPhotoProcessing.process(data) }.value
            photo = .local(processed)
            onChanged()
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
            isProcessingFailedAlertVisible = true
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private static var diameter: CGFloat { 120 }
    private static var badgeDiameter: CGFloat { 36 }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 24) {
        FoodPhotoPicker(photo: .constant(.none))
        FoodPhotoPicker(photo: .constant(.none), isMissing: true)
    }
}
