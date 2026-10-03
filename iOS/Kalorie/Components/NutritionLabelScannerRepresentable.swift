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
    let onProgress: (Double) -> Void
    let onRecognized: (NutritionLabelReading, String, String?) async -> Void

    // MARK: - Nested class

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        private let onProgress: (Double) -> Void
        private let onRecognized: (NutritionLabelReading, String, String?) async -> Void
        private var liveBarcode: String?
        private var readings: [NutritionLabelReading] = []
        private var windowStartedAt: Date?
        private var isDelivering = false
        private var lastParseAt = Date.distantPast

        private static let parseThrottleInterval: TimeInterval = 0.3
        private static let scanWindow: TimeInterval = 3

        init(onProgress: @escaping (Double) -> Void, onRecognized: @escaping (NutritionLabelReading, String, String?) async -> Void) {
            self.onProgress = onProgress
            self.onRecognized = onRecognized
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            process(allItems: allItems, dataScanner: dataScanner)
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            process(allItems: allItems, dataScanner: dataScanner)
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didRemove removedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            process(allItems: allItems, dataScanner: dataScanner)
        }

        // The window starts at the first update that shows part of a table and restarts whenever an update shows
        // none; the user holds the phone over the label for scanWindow, every update is read and the readings are
        // merged at the end, so a digit misread in one update is outvoted by the others.
        private func process(allItems: [RecognizedItem], dataScanner: DataScannerViewController) {
            for item in allItems {
                if case .barcode(let barcode) = item, liveBarcode == nil, let payload = barcode.payloadStringValue {
                    liveBarcode = payload
                }
            }
            guard !isDelivering else { return }
            let now = Date()
            guard now.timeIntervalSince(lastParseAt) >= Self.parseThrottleInterval else { return }
            lastParseAt = now
            let viewSize = dataScanner.view.bounds.size
            let lines: [RecognizedTextLine] = allItems.compactMap { item in
                guard case .text(let text) = item else { return nil }
                return LiveTextItemConversion.recognizedTextLine(transcript: text.transcript, bounds: text.bounds, in: viewSize)
            }
            let reading = NutritionLabelParser.parse(lines: lines)
            guard reading.recognizedFields.contains(where: { $0 != .measure }) else {
                readings.removeAll()
                windowStartedAt = nil
                onProgress(0)
                return
            }
            let startedAt = windowStartedAt ?? now
            windowStartedAt = startedAt
            readings.append(reading)
            let elapsed = now.timeIntervalSince(startedAt)
            guard elapsed >= Self.scanWindow else {
                onProgress(elapsed / Self.scanWindow)
                return
            }
            let merged = NutritionLabelReadingMerger.merge(readings)
            let ocrText = lines.map(\.text).joined(separator: "\n")
            readings.removeAll()
            windowStartedAt = nil
            onProgress(0)
            isDelivering = true
            let barcode = liveBarcode
            Task { [weak self] in
                await self?.onRecognized(merged, ocrText, barcode)
                self?.isDelivering = false
            }
        }
    }

    // MARK: - Functions

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let dataScannerVC = DataScannerViewController(
            recognizedDataTypes: [.text(), .barcode()],
            qualityLevel: .accurate,
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
        if isProcessing {
            uiViewController.stopScanning()
        } else {
            try? uiViewController.startScanning()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onProgress: onProgress, onRecognized: onRecognized)
    }
}
