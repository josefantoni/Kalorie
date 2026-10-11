//
//  RecognizedTextLine.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import CoreGraphics

struct RecognizedTextLine: Codable, Equatable {

    // MARK: - Properties

    let text: String
    let boundingBox: CGRect
    let angleDegrees: Double

    // MARK: - Init

    init(text: String, boundingBox: CGRect, angleDegrees: Double = 0) {
        self.text = text
        self.boundingBox = boundingBox
        self.angleDegrees = angleDegrees
    }
}
