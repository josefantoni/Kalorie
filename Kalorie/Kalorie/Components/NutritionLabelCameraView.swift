//
//  NutritionLabelCameraView.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import SwiftUI
import UIKit

struct NutritionLabelCameraView: View {

    // MARK: - Properties

    let isRecognizing: Bool
    let hint: String?
    let onCaptured: (UIImage, String?) async -> Void
    let onClose: () -> Void

    @State private var manualCaptureRequest = 0
    @State private var scannerResetToken = 0
    @State private var captureFailureMessage: String?
    @State private var isAutoCaptureEnabled = true

    // MARK: - Body

    var body: some View {
        ZStack {
            NutritionLabelScannerRepresentable(
                isProcessing: isRecognizing,
                isAutoCaptureEnabled: isAutoCaptureEnabled,
                manualCaptureRequest: manualCaptureRequest,
                onCaptured: onCaptured
            ) {
                // Auto-capture is disabled here, not just retried: on a device where capturePhoto()
                // itself fails, the very next live frame re-clears the same auto-capture predicate
                // and re-triggers it immediately, turning a single failure into a tight fail/reset
                // loop (observed on-device as repeated captureSource errors in a row). Disabling it
                // leaves the shutter as an explicit, user-paced way to keep trying.
                captureFailureMessage = L10n.AddFood.nutritionLabelCameraCaptureFailed
                isAutoCaptureEnabled = false
                scannerResetToken += 1
            }
            .id(scannerResetToken)
            .ignoresSafeArea()

            // The progress overlay must render below the controls, not after them: it used to be
            // the topmost ZStack layer and visually swallowed the close button along with the
            // shutter while isRecognizing was true, leaving no way to even dismiss the camera.
            if isRecognizing {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.ultraThinMaterial)
            }

            VStack {
                HStack {
                    Spacer()
                    BaseButton(style: .plain, imageName: .close, imageSize: .medium) {
                        onClose()
                    }
                    .foregroundStyle(.white)
                    .padding()
                }
                Spacer()
                Text(captureFailureMessage ?? hint ?? L10n.AddFood.nutritionLabelCameraIdleHint)
                    .font(.footnote)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.55), in: .rect(cornerRadius: 12))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                shutterButton
                    .padding(.bottom, 24)
            }
        }
        .background(.black)
        .onChange(of: isRecognizing) { _, isRecognizing in
            if isRecognizing { captureFailureMessage = nil }
        }
    }

    // MARK: - Functions

    private var shutterButton: some View {
        Button {
            manualCaptureRequest += 1
        } label: {
            Circle()
                .strokeBorder(.white, lineWidth: 4)
                .frame(width: 72, height: 72)
                .overlay(Circle().fill(.white).frame(width: 58, height: 58))
        }
        .disabled(isRecognizing)
        .accessibilityLabel(L10n.AddFood.nutritionLabelCameraShutterAccessibility)
    }
}

// MARK: - Preview

#Preview {
    NutritionLabelCameraView(isRecognizing: false, hint: nil, onCaptured: { _, _ in }, onClose: {})
}
