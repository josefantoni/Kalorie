//
//  RecognizeNutritionLabelUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 13.09.2026.
//

import XCTest
@testable import Kalorie

final class RecognizeNutritionLabelUseCaseTests: XCTestCase {

    func test_callAsFunction_whenNothingRecognized_throwsNothingRecognized() async {
        let sut = makeSUT(textLines: [], barcode: nil, candidate: nil)
        do {
            _ = try await sut(image: makeImage())
            XCTFail("expected nothingRecognized to be thrown")
        } catch NutritionLabelRecognitionError.nothingRecognized {
            // expected
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }

    func test_callAsFunction_whenOnlyABarcodeIsFound_returnsIt() async throws {
        let sut = makeSUT(textLines: [], barcode: "12345678", candidate: nil)
        let reading = try await sut(image: makeImage())
        XCTAssertEqual(reading.scannedCode, "12345678")
    }

    func test_callAsFunction_mergesTheModelCandidateOnTopOfTheParsedRows() async throws {
        let lines = [
            RecognizedTextLine(text: "100 g", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.15, height: 0.03)),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Tvaroh", boundingBox: CGRect(x: 0.1, y: 0.98, width: 0.3, height: 0.02))
        ]
        let candidate = NutritionLabelModelCandidate(name: "Tvaroh")
        let sut = makeSUT(textLines: lines, barcode: nil, candidate: candidate)

        let reading = try await sut(image: makeImage())

        XCTAssertEqual(reading.fat, 12)
        XCTAssertEqual(reading.name, "Tvaroh")
    }

    func test_callAsFunction_whenImageHasNoCGImage_throwsInvalidImage() async {
        let sut = makeSUT(textLines: [], barcode: nil, candidate: nil)
        do {
            _ = try await sut(image: UIImage())
            XCTFail("expected invalidImage to be thrown")
        } catch NutritionLabelRecognitionError.invalidImage {
            // expected
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }

    // MARK: - Image orientation

    func test_cgImagePropertyOrientation_matchesEachUIImageOrientationOneToOne() {
        // A UIImage's cgImage carries only raw sensor pixels, never the UIImage's own
        // imageOrientation — passing the wrong Vision orientation reads a portrait photo's text
        // sideways, which is exactly what broke row/column geometry on a real device (protein read
        // as "3" from an unrelated label two rows away, because the table's rows and columns
        // collapsed into two disjoint bands instead of pairing up).
        let expected: [(UIImage.Orientation, CGImagePropertyOrientation)] = [
            (.up, .up), (.upMirrored, .upMirrored),
            (.down, .down), (.downMirrored, .downMirrored),
            (.left, .left), (.leftMirrored, .leftMirrored),
            (.right, .right), (.rightMirrored, .rightMirrored)
        ]
        for (uiOrientation, cgOrientation) in expected {
            XCTAssertEqual(CGImagePropertyOrientation(uiOrientation), cgOrientation, "\(uiOrientation) must map to \(cgOrientation)")
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        textLines: [RecognizedTextLine],
        barcode: String?,
        candidate: NutritionLabelModelCandidate?
    ) -> RecognizeNutritionLabelUseCase {
        RecognizeNutritionLabelUseCase(
            textRecognizer: TextRecognizerFake(stubbedLines: textLines),
            barcodeDetector: BarcodeDetectorFake(stubbedBarcode: barcode),
            modelExtractor: NutritionLabelModelExtractorFake(stubbedCandidate: candidate)
        )
    }

    private func makeImage() -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 10, height: 10))
        return renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 10, height: 10))
        }
    }
}
