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
}
