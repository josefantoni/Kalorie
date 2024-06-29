//
//  DataScannerRepresentable.swift
//  Kalorie
//
//  Created by Josef Antoni on 18.06.2024.
//

import Foundation
import SwiftUI
import VisionKit

enum ScannerErrorType: Error {
    case notFound
}


struct DataScannerRepresentable: UIViewControllerRepresentable {
    
    // MARK: - Properties
    
    @Binding var shouldStartScanning: Bool
    @Binding var scannedCode: String?
    var coordinator: Coordinator?
    
    // MARK: - Nested class
    
    class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var parent: DataScannerRepresentable
        
        init(_ parent: DataScannerRepresentable) {
            self.parent = parent
        }
        
        func dataScanner(_ dataScanner: DataScannerViewController, didUpdate updatedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard let item = allItems.first else {
                // TODO: log something went wrong
                return
            }
            switch item {
            case .barcode(let barcode):
                if let code = barcode.payloadStringValue {
                    parent.scannedCode = code
                    parent.shouldStartScanning = false
                    parent.coordinator = nil
                } else {
                    // TODO: payload empty throw?
                    print("payload empty!")
                }
            default:
                // TODO: alert with msg 'only barcode?'
                print("unexpected item")
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
        do {
            try uiViewController.startScanning()
        } catch {
            print("Error while scanning barcode")
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
}
