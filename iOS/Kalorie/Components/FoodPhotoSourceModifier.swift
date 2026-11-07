//
//  FoodPhotoSourceModifier.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import PhotosUI
import SwiftUI

private struct FoodPhotoSourceModifier: ViewModifier {

    // MARK: - Properties

    @Binding var isPresented: Bool
    @Binding var isProcessing: Bool
    let showAction: (() -> Void)?
    let cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol
    let onProcessed: @MainActor (Data) async -> Void

    @State private var isLibraryVisible = false
    @State private var isCameraVisible = false
    @State private var isCameraDeniedAlertVisible = false
    @State private var isProcessingFailedAlertVisible = false
    @State private var libraryItem: PhotosPickerItem?

    // MARK: - Body

    func body(content: Content) -> some View {
        content
            .confirmationDialog(L10n.FoodPhoto.title, isPresented: $isPresented, titleVisibility: .hidden) {
                if let showAction {
                    Button(L10n.FoodPhoto.actionShow) { showAction() }
                }
                if !ProcessInfo.isRunningOnMac && UIImagePickerController.isSourceTypeAvailable(.camera) {
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
            .alert(L10n.AddFood.cameraPermissionAlert, isPresented: $isCameraDeniedAlertVisible) {
                Button(L10n.AddFood.buttonOpenSettings) { openSettings() }
                Button(L10n.Common.buttonCancel, role: .cancel) {}
            }
            .alert(L10n.Common.errorUnknown, isPresented: $isProcessingFailedAlertVisible) {
                Button(L10n.Common.ok) {}
            }
    }

    // MARK: - Functions

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
        isProcessing = true
        defer { isProcessing = false }
        do {
            let processed = try await Task.detached { try FoodPhotoProcessing.process(data) }.value
            await onProcessed(processed)
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
            isProcessingFailedAlertVisible = true
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

extension View {
    func foodPhotoSource(
        isPresented: Binding<Bool>,
        isProcessing: Binding<Bool> = .constant(false),
        showAction: (() -> Void)? = nil,
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProvider(),
        onProcessed: @escaping @MainActor (Data) async -> Void
    ) -> some View {
        modifier(
            FoodPhotoSourceModifier(
                isPresented: isPresented,
                isProcessing: isProcessing,
                showAction: showAction,
                cameraAuthorizationProvider: cameraAuthorizationProvider,
                onProcessed: onProcessed
            )
        )
    }
}
