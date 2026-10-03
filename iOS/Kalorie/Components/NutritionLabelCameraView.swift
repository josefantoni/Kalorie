//
//  NutritionLabelCameraView.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import SwiftUI

struct NutritionLabelCameraView: View {

    // MARK: - Properties

    let isRecognizing: Bool
    let hint: String?
    let onRecognized: (NutritionLabelReading, String, String?) async -> Void
    let onClose: () -> Void

    @State private var progress: Double = 0

    // MARK: - Body

    var body: some View {
        ZStack {
            NutritionLabelScannerRepresentable(
                isProcessing: isRecognizing,
                onProgress: { progress = $0 },
                onRecognized: onRecognized
            )
            .ignoresSafeArea()

            // The progress overlay must render below the controls, not after them: it used to be
            // the topmost ZStack layer and visually swallowed the close button while isRecognizing
            // was true, leaving no way to even dismiss the camera.
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
                Text(hint ?? L10n.AddFood.nutritionLabelCameraIdleHint)
                    .font(.footnote)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.55), in: .rect(cornerRadius: 12))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                ProgressView(value: progress)
                    .tint(.white)
                    .containerRelativeFrame(.horizontal) { width, _ in width * 0.6 }
                    .padding(.bottom, 32)
            }
        }
        .background(.black)
    }
}

// MARK: - Preview

#Preview {
    NutritionLabelCameraView(isRecognizing: false, hint: nil, onRecognized: { _, _, _ in }) {}
}
