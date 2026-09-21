//
//  NutritionLabelPrefilling.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation
import UIKit

protocol NutritionLabelPrefilling: AnyObject {
    var formInput: FoodItemFormInput { get set }
    var recognizedFields: Set<FoodItemFormField> { get set }
    var isRecognizingNutritionLabel: Bool { get set }
    var isNutritionLabelCameraVisible: Bool { get set }
    var nutritionLabelCameraHint: String? { get set }
    var alertItem: AlertItem? { get set }
}

extension NutritionLabelPrefilling {

    @MainActor
    @discardableResult
    func recognizeNutritionLabel(
        from image: UIImage,
        liveBarcode: String?,
        using recognizeNutritionLabel: any RecognizeNutritionLabelUseCaseProtocol
    ) async -> Bool {
        guard !isRecognizingNutritionLabel else { return false }
        isRecognizingNutritionLabel = true
        defer { isRecognizingNutritionLabel = false }
        do {
            var reading = try await recognizeNutritionLabel(image: image)
            reading.scannedCode = reading.scannedCode ?? liveBarcode
            guard !reading.recognizedFields.isEmpty else {
                nutritionLabelCameraHint = L10n.AddFood.nutritionLabelNothingRecognized
                return false
            }
            recognizedFields.formUnion(formInput.applying(reading, alreadyRecognizedFields: recognizedFields))
            nutritionLabelCameraHint = nil
            isNutritionLabelCameraVisible = false
            return true
        } catch NutritionLabelRecognitionError.nothingRecognized {
            nutritionLabelCameraHint = L10n.AddFood.nutritionLabelNothingRecognized
            return false
        } catch {
            Log.warning(error, category: Constants.LogCategory.nutritionLabelRecognition)
            nutritionLabelCameraHint = L10n.AddFood.nutritionLabelNothingRecognized
            return false
        }
    }

    @MainActor
    func openNutritionLabelCamera(using cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol, onDenied: () -> Void = {}) async {
        switch cameraAuthorizationProvider.status {
        case .authorized:
            isNutritionLabelCameraVisible = true
        case .notDetermined:
            if await cameraAuthorizationProvider.requestAccess() {
                isNutritionLabelCameraVisible = true
            } else {
                onDenied()
            }
        case .denied, .unsupported:
            onDenied()
        }
    }

    func onFormFieldEdited(_ field: FoodItemFormField) {
        recognizedFields.remove(field)
    }
}
