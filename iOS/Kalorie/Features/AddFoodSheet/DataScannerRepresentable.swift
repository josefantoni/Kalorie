//
//  DataScannerRepresentable.swift
//  Kalorie
//
//  Created by Josef Antoni on 18.06.2024.
//

import Foundation
import SwiftUI
import VisionKit

struct DataScannerRepresentable: UIViewControllerRepresentable {

    // MARK: - Properties

    @Binding var scannedCode: String
    let isSearching: Bool

    // MARK: - Nested class

    class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var parent: DataScannerRepresentable
        private var lastDeliveredCode: String?
        private var wasSearching = false

        init(_ parent: DataScannerRepresentable) {
            self.parent = parent
        }

        func resetIfSearchEnded(isSearching: Bool) {
            if wasSearching && !isSearching {
                lastDeliveredCode = nil
            }
            wasSearching = isSearching
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard let item = allItems.first else { return }
            switch item {
            case .barcode(let barcode):
                if let code = barcode.payloadStringValue {
                    guard code != lastDeliveredCode else { return }
                    lastDeliveredCode = code
                    parent.scannedCode = code
                } else {
                    assertionFailure("Barcode recognized but payloadStringValue is nil")
                }
            default:
                assertionFailure("Unexpected recognized item type: \(item)")
            }
        }
    }

    // MARK: - Functions

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let dataScannerVC = DataScannerViewController(
            recognizedDataTypes: [.barcode()],
            qualityLevel: .accurate,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: true,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        dataScannerVC.delegate = context.coordinator
        return dataScannerVC
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {
        context.coordinator.resetIfSearchEnded(isSearching: isSearching)
        if isSearching {
            uiViewController.stopScanning()
        } else {
            try? uiViewController.startScanning()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
}
