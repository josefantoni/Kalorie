//
//  LiveTextItemConversionTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.09.2026.
//

import XCTest
@testable import Kalorie

final class LiveTextItemConversionTests: XCTestCase {

    // MARK: - Tests

    func test_recognizedTextLine_upperLineInViewPoints_getsLargerMidYThanLowerLine() {
        let viewSize = CGSize(width: 400, height: 800)

        let upper = LiveTextItemConversion.recognizedTextLine(
            transcript: "upper",
            topLeft: CGPoint(x: 20, y: 100),
            topRight: CGPoint(x: 200, y: 100),
            bottomLeft: CGPoint(x: 20, y: 140),
            bottomRight: CGPoint(x: 200, y: 140),
            in: viewSize
        )
        let lower = LiveTextItemConversion.recognizedTextLine(
            transcript: "lower",
            topLeft: CGPoint(x: 20, y: 400),
            topRight: CGPoint(x: 200, y: 400),
            bottomLeft: CGPoint(x: 20, y: 440),
            bottomRight: CGPoint(x: 200, y: 440),
            in: viewSize
        )

        XCTAssertGreaterThan(
            upper.boundingBox.midY,
            lower.boundingBox.midY,
            "the parser sorts by midY descending as top-first, so a line above another in view points must convert to a larger midY, or every row comes out upside down"
        )
    }

    func test_recognizedTextLine_normalizesToZeroToOneWithBottomLeftOrigin() {
        let line = LiveTextItemConversion.recognizedTextLine(
            transcript: "line",
            topLeft: CGPoint(x: 0, y: 0),
            topRight: CGPoint(x: 100, y: 0),
            bottomLeft: CGPoint(x: 0, y: 50),
            bottomRight: CGPoint(x: 100, y: 50),
            in: CGSize(width: 100, height: 200)
        )

        XCTAssertEqual(line.boundingBox.minX, 0, accuracy: 0.0001)
        XCTAssertEqual(line.boundingBox.width, 1, accuracy: 0.0001)
        XCTAssertEqual(line.boundingBox.height, 0.25, accuracy: 0.0001, "a 50pt-tall line in a 200pt-tall view is 0.25 of normalized height")
        XCTAssertEqual(line.boundingBox.minY, 0.75, accuracy: 0.0001, "a line pinned to the top of the view (view-points y=0) must land at the top of normalized bottom-left-origin space (y=0.75...1)")
    }

    func test_recognizedTextLine_zeroSizedViewFallsBackToZeroRectRatherThanDividingByZero() {
        let line = LiveTextItemConversion.recognizedTextLine(
            transcript: "line",
            topLeft: .zero,
            topRight: CGPoint(x: 10, y: 0),
            bottomLeft: CGPoint(x: 0, y: 10),
            bottomRight: CGPoint(x: 10, y: 10),
            in: .zero
        )

        XCTAssertEqual(line.boundingBox, .zero)
    }
}
