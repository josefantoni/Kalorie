//
//  NutritionLabelPrefilling.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation

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
    func applyRecognizedNutritionLabel(
        _ recognized: NutritionLabelReading,
        ocrText: String,
        liveBarcode: String?,
        using modelExtractor: any NutritionLabelModelExtractorProtocol
    ) async -> Bool {
        guard !isRecognizingNutritionLabel else { return false }
        isRecognizingNutritionLabel = true
        defer { isRecognizingNutritionLabel = false }
        var reading = recognized
        if let candidate = await modelExtractor(ocrText: ocrText) {
            reading = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)
        }
        reading.scannedCode = reading.scannedCode ?? liveBarcode
        guard !reading.recognizedFields.isEmpty else {
            nutritionLabelCameraHint = L10n.AddFood.nutritionLabelNothingRecognized
            return false
        }
        recognizedFields.formUnion(formInput.applying(reading, alreadyRecognizedFields: recognizedFields))
        nutritionLabelCameraHint = nil
        isNutritionLabelCameraVisible = false
        return true
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
