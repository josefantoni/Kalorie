//
//  BarcodeScannerOverlay.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.09.2026.
//

import SwiftUI

struct BarcodeScannerOverlay: View {

    // MARK: - Properties

    @Binding var scannedCode: String
    let isSearching: Bool
    let onClose: () -> Void

    // MARK: - Body

    var body: some View {
        ZStack {
            DataScannerRepresentable(scannedCode: $scannedCode, isSearching: isSearching)
                .ignoresSafeArea()

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
            }

            if isSearching {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.ultraThinMaterial)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    BarcodeScannerOverlay(scannedCode: .constant(""), isSearching: false) {}
}
