//
//  ActivityView.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI
import UIKit

struct ActivityView: UIViewControllerRepresentable {

    // MARK: - Properties

    let url: URL
    let onFinished: () -> Void

    // MARK: - Functions

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, _, _, _ in
            onFinished()
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
