//
//  WeightSparklineTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 28.10.2026.
//

import XCTest
@testable import Kalorie

final class WeightSparklineTests: XCTestCase {

    // MARK: - Tests

    func test_newestWeight_isTheCentreOfTheGraph() {
        let positions = WeightSparkline(points: [79.0, 79.4, 78.4], minimumSpan: 2).positions(in: rect)

        XCTAssertEqual(positions.last, CGPoint(x: rect.midX, y: rect.midY), "the weight valid on the selected day is the dot in the middle")
    }

    func test_olderWeights_extendToTheLeftInOrder_oldestAtTheEdge() {
        let positions = WeightSparkline(points: [79.0, 79.4, 78.4], minimumSpan: 2).positions(in: rect)

        XCTAssertEqual(positions.first?.x, rect.minX)
        XCTAssertEqual(positions.map(\.x), positions.map(\.x).sorted())
        XCTAssertTrue(positions.allSatisfy { $0.x <= rect.midX })
    }

    func test_smallWobble_isNotDrawnAsACliff_becauseOfTheMinimumSpan() {
        let positions = WeightSparkline(points: [78.6, 78.4], minimumSpan: 2).positions(in: rect)

        let drop = abs((positions.first?.y ?? 0) - rect.midY)
        XCTAssertLessThan(drop, rect.height / 2 * 0.25, "0.2 kg on a 2 kg minimum span must stay a gentle slope")
    }

    func test_higherEarlierWeight_isDrawnAboveTheDot() {
        let positions = WeightSparkline(points: [80.0, 78.4], minimumSpan: 2).positions(in: rect)

        XCTAssertLessThan(positions[0].y, positions[1].y)
    }

    func test_singleWeight_drawsNothing() {
        XCTAssertTrue(WeightSparkline(points: [78.4], minimumSpan: 2).positions(in: rect).isEmpty)
    }

    // MARK: - Helpers

    private let rect = CGRect(x: 0, y: 0, width: 56, height: 16)
}
