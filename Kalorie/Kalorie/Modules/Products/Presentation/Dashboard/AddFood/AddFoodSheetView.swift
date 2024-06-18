//
//  AddFoodSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.06.2024.
//

import Foundation
import SwiftUI
import VisionKit


struct AddFoodSheetView: View {
    
    // MARK: - Properties
    
    @Environment(\.dismiss) var dismiss
    @State private var isScannerVisible: Bool
    @State private var scannedCode: String = ""
    
    
    // MARK: - Init

    init(withBarcodeScan: Bool) {
        self.isScannerVisible = withBarcodeScan
    }
    
    
    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.largeTitle)
                        .fontWeight(.light)
                }
                .padding([.top, .trailing])
            }
            ZStack {
                List {
                    Text("scanned code: \(scannedCode)")
                }
                if isScannerVisible {
                    startDataScanner
                }
            }
        }
    }
    
    @ViewBuilder @MainActor var startDataScanner: some View {
        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            ZStack(alignment: .bottom) {
                DataScannerRepresentable(shouldStartScanning: $isScannerVisible, scannedCode: $scannedCode)
            }
        } else if !DataScannerViewController.isSupported {
            Text("It looks like this device doesn't support the DataScannerViewController")
        } else {
            Text("It appears your camera may not be available")
        }
    }
}


// MARK: - Preview

#Preview {
    AddFoodSheetView(withBarcodeScan: false)
}
