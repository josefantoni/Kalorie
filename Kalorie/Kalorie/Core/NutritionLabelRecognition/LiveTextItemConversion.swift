//
//  LiveTextItemConversion.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import CoreGraphics

enum LiveTextItemConversion {

    // MARK: - Functions

    static func recognizedTextLine(
        transcript: String,
        topLeft: CGPoint,
        topRight: CGPoint,
        bottomLeft: CGPoint,
        bottomRight: CGPoint,
        in viewSize: CGSize
    ) -> RecognizedTextLine {
        let xs = [topLeft.x, topRight.x, bottomLeft.x, bottomRight.x]
        let ys = [topLeft.y, topRight.y, bottomLeft.y, bottomRight.y]
        let minX = xs.min() ?? 0
        let maxX = xs.max() ?? 0
        let minY = ys.min() ?? 0
        let maxY = ys.max() ?? 0

        guard viewSize.width > 0, viewSize.height > 0 else {
            return RecognizedTextLine(text: transcript, boundingBox: .zero)
        }

        return RecognizedTextLine(
            text: transcript,
            boundingBox: CGRect(
                x: minX / viewSize.width,
                y: 1 - maxY / viewSize.height,
                width: (maxX - minX) / viewSize.width,
                height: (maxY - minY) / viewSize.height
            )
        )
    }
}
