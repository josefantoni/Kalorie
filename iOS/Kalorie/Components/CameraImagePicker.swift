//
//  CameraImagePicker.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import SwiftUI
import UIKit

struct CameraImagePicker: UIViewControllerRepresentable {

    // MARK: - Properties

    let onCaptured: (Data) -> Void
    let onCancelled: () -> Void

    // MARK: - UIViewControllerRepresentable

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCaptured: onCaptured, onCancelled: onCancelled)
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

        // MARK: - Properties

        private let onCaptured: (Data) -> Void
        private let onCancelled: () -> Void

        // MARK: - Init

        init(onCaptured: @escaping (Data) -> Void, onCancelled: @escaping () -> Void) {
            self.onCaptured = onCaptured
            self.onCancelled = onCancelled
        }

        // MARK: - Functions

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            guard
                let image = info[.originalImage] as? UIImage,
                let data = image.jpegData(compressionQuality: 1)
            else {
                onCancelled()
                return
            }
            onCaptured(data)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCancelled()
        }
    }
}
