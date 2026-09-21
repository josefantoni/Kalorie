//
//  RecognizeNutritionLabelUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation
import UIKit

enum NutritionLabelRecognitionError: Error {
    case invalidImage
    case nothingRecognized
}

extension CGImagePropertyOrientation {
    // A UIImage's cgImage carries only the raw sensor pixels, not the UIImage's own
    // imageOrientation — feeding that cgImage to Vision without this conversion made Vision read a
    // portrait capture's text sideways, breaking the row/column geometry NutritionLabelParser
    // depends on (confirmed on-device: bounding boxes came back narrow-and-tall instead of
    // wide-and-short, one axis apart from what a human sees).
    init(_ uiOrientation: UIImage.Orientation) {
        switch uiOrientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

protocol RecognizeNutritionLabelUseCaseProtocol {
    func callAsFunction(image: UIImage) async throws -> NutritionLabelReading
}

struct RecognizeNutritionLabelUseCase: RecognizeNutritionLabelUseCaseProtocol {

    // MARK: - Properties

    private let textRecognizer: any TextRecognizerProtocol
    private let barcodeDetector: any BarcodeDetectorProtocol
    private let modelExtractor: any NutritionLabelModelExtractorProtocol

    // MARK: - Init

    init(
        textRecognizer: any TextRecognizerProtocol = VisionTextRecognizer(),
        barcodeDetector: any BarcodeDetectorProtocol = VisionTextRecognizer(),
        modelExtractor: any NutritionLabelModelExtractorProtocol = FoundationModelExtractor()
    ) {
        self.textRecognizer = textRecognizer
        self.barcodeDetector = barcodeDetector
        self.modelExtractor = modelExtractor
    }

    // MARK: - Functions

    func callAsFunction(image: UIImage) async throws -> NutritionLabelReading {
        guard let cgImage = image.cgImage else { throw NutritionLabelRecognitionError.invalidImage }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)

        async let linesTask = textRecognizer.recognizeText(in: cgImage, orientation: orientation)
        async let barcodeTask = try? barcodeDetector.detectBarcode(in: cgImage, orientation: orientation)

        let lines = try await linesTask
        let barcode = await barcodeTask

        var reading = NutritionLabelParser.parse(lines: lines)
        reading.scannedCode = barcode

        let ocrText = lines.map(\.text).joined(separator: "\n")
        if let candidate = await modelExtractor(ocrText: ocrText) {
            reading = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)
        }

        guard !reading.isEmpty else {
            Log.logger(Constants.LogCategory.nutritionLabelRecognition).warning("rows=\(lines.count, privacy: .public) fields=0")
            throw NutritionLabelRecognitionError.nothingRecognized
        }
        return reading
    }
}

#if DEBUG
struct RecognizeNutritionLabelUseCaseFake: RecognizeNutritionLabelUseCaseProtocol {
    var stubbedReading = NutritionLabelReading()
    var errorToThrow: Error?

    func callAsFunction(image: UIImage) async throws -> NutritionLabelReading {
        if let errorToThrow { throw errorToThrow }
        return stubbedReading
    }
}
#endif
