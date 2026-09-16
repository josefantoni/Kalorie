//
//  NutritionLabelScannerRepresentable.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import SwiftUI
import UIKit
import VisionKit

extension LiveTextItemConversion {
    static func recognizedTextLine(transcript: String, bounds: RecognizedItem.Bounds, in viewSize: CGSize) -> RecognizedTextLine {
        recognizedTextLine(
            transcript: transcript,
            topLeft: bounds.topLeft,
            topRight: bounds.topRight,
            bottomLeft: bounds.bottomLeft,
            bottomRight: bounds.bottomRight,
            in: viewSize
        )
    }
}

struct NutritionLabelScannerRepresentable: UIViewControllerRepresentable {

    // MARK: - Properties

    let isProcessing: Bool
    let isAutoCaptureEnabled: Bool
    let manualCaptureRequest: Int
    let onCaptured: (UIImage, String?) async -> Void
    let onCaptureFailed: () -> Void

    // MARK: - Nested class

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        private let onCaptured: (UIImage, String?) async -> Void
        private let onCaptureFailed: () -> Void
        private var lastManualCaptureRequest: Int
        private var liveBarcode: String?
        private var isCapturing = false
        private var lastParseAt = Date.distantPast
        var isAutoCaptureEnabled = true

        private static let parseThrottleInterval: TimeInterval = 0.3

        init(onCaptured: @escaping (UIImage, String?) async -> Void, onCaptureFailed: @escaping () -> Void, manualCaptureRequest: Int) {
            self.onCaptured = onCaptured
            self.onCaptureFailed = onCaptureFailed
            self.lastManualCaptureRequest = manualCaptureRequest
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            process(allItems: allItems, dataScanner: dataScanner)
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            process(allItems: allItems, dataScanner: dataScanner)
        }

        func requestManualCapture(from dataScanner: DataScannerViewController, request: Int) {
            guard request != lastManualCaptureRequest else { return }
            lastManualCaptureRequest = request
            capture(from: dataScanner)
        }

        private func process(allItems: [RecognizedItem], dataScanner: DataScannerViewController) {
            for item in allItems {
                if case .barcode(let barcode) = item, liveBarcode == nil, let payload = barcode.payloadStringValue {
                    liveBarcode = payload
                }
            }
            guard isAutoCaptureEnabled, !isCapturing else { return }
            // The delegate fires many times per second as tracked items move; re-running the
            // multi-keyword parse on every single callback added steady CPU load with no benefit,
            // since a genuine label completes over many frames, not one.
            let now = Date()
            guard now.timeIntervalSince(lastParseAt) >= Self.parseThrottleInterval else { return }
            lastParseAt = now
            let viewSize = dataScanner.view.bounds.size
            let lines: [RecognizedTextLine] = allItems.compactMap { item in
                guard case .text(let text) = item else { return nil }
                return LiveTextItemConversion.recognizedTextLine(transcript: text.transcript, bounds: text.bounds, in: viewSize)
            }
            guard NutritionLabelParser.parse(lines: lines).isCompleteForAutoCapture else { return }
            capture(from: dataScanner)
        }

        private func capture(from dataScanner: DataScannerViewController) {
            guard !isCapturing else { return }
            isCapturing = true
            let barcode = liveBarcode
            // isCapturing stays held for the whole round trip (capture through merge-or-hint), not
            // just the capturePhoto() call: the isProcessing binding's own updateUIViewController
            // cycle only reacts once the VM flips isRecognizingNutritionLabel, which lags a beat
            // behind capturePhoto() returning, and a live update landing in that gap used to fire a
            // second, overlapping capturePhoto() call while the first was still being recognized.
            //
            // Do NOT call stopScanning() before capturePhoto(): capturePhoto() is documented to work
            // against the still-running live session, and stopping it immediately beforehand was
            // observed on-device to throw AVFoundationErrorDomain -11800 followed by a capture-session
            // runtime error, which stopScanning()/startScanning() cannot recover from — only tearing
            // down and recreating the AVCaptureSession can, which is why a failure here goes through
            // onCaptureFailed rather than a resume attempt.
            Task { [weak self] in
                defer { self?.isCapturing = false }
                do {
                    let image = try await dataScanner.capturePhoto()
                    await self?.onCaptured(image, barcode)
                    try? await dataScanner.startScanning()
                } catch {
                    Log.warning(error, category: Constants.LogCategory.nutritionLabelRecognition)
                    self?.onCaptureFailed()
                }
            }
        }
    }

    // MARK: - Functions

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let dataScannerVC = DataScannerViewController(
            recognizedDataTypes: [.text(), .barcode()],
            qualityLevel: .balanced,
            recognizesMultipleItems: true,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: false,
            isHighlightingEnabled: true
        )
        dataScannerVC.delegate = context.coordinator
        try? dataScannerVC.startScanning()
        return dataScannerVC
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {
        context.coordinator.isAutoCaptureEnabled = isAutoCaptureEnabled
        if isProcessing {
            uiViewController.stopScanning()
        } else {
            try? uiViewController.startScanning()
        }
        context.coordinator.requestManualCapture(from: uiViewController, request: manualCaptureRequest)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onCaptured: onCaptured, onCaptureFailed: onCaptureFailed, manualCaptureRequest: manualCaptureRequest)
    }
}
