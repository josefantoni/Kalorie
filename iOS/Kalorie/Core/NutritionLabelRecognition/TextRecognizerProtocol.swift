//
//  TextRecognizerProtocol.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import CoreGraphics
import Vision

protocol TextRecognizerProtocol {
    func recognizeText(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [RecognizedTextLine]
}

protocol BarcodeDetectorProtocol {
    func detectBarcode(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> String?
}

struct VisionTextRecognizer: TextRecognizerProtocol, BarcodeDetectorProtocol {

    // MARK: - Functions

    func recognizeText(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [RecognizedTextLine] {
        // handler.perform is synchronous and CPU-heavy; without this hop it would run inline on
        // whatever actor called this async func (typically @MainActor), blocking the UI for the
        // duration of the accurate-level OCR pass.
        try await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["cs-CZ", "pl-PL", "de-DE", "en-US"]
            request.usesLanguageCorrection = false
            let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
            try handler.perform([request])
            return (request.results ?? []).compactMap { observation -> RecognizedTextLine? in
                guard let candidate = observation.topCandidates(1).first else { return nil }
                return RecognizedTextLine(text: candidate.string, boundingBox: observation.boundingBox)
            }
        }.value
    }

    func detectBarcode(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> String? {
        try await Task.detached(priority: .userInitiated) {
            let request = VNDetectBarcodesRequest()
            let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
            try handler.perform([request])
            return (request.results ?? []).first?.payloadStringValue
        }.value
    }
}

#if DEBUG
struct TextRecognizerFake: TextRecognizerProtocol {
    var stubbedLines: [RecognizedTextLine] = []
    var errorToThrow: Error?

    func recognizeText(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [RecognizedTextLine] {
        if let errorToThrow { throw errorToThrow }
        return stubbedLines
    }
}

struct BarcodeDetectorFake: BarcodeDetectorProtocol {
    var stubbedBarcode: String?

    func detectBarcode(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> String? { stubbedBarcode }
}
#endif
