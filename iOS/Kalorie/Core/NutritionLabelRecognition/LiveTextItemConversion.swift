//
//  LiveTextItemConversion.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.09.2026.
//

import CoreGraphics

enum LiveTextItemConversion {

    // MARK: - Properties

    private static let maxSplitAngleDegrees = 10.0

    // MARK: - Functions

    // A paragraph item holds several printed lines separated by newlines; its box is divided evenly between them so
    // the rows keep a position of their own. Only upright text is divided, a rotated box has no vertical axis to cut.
    static func recognizedTextLines(
        transcript: String,
        topLeft: CGPoint,
        topRight: CGPoint,
        bottomLeft: CGPoint,
        bottomRight: CGPoint,
        in viewSize: CGSize
    ) -> [RecognizedTextLine] {
        let whole = recognizedTextLine(
            transcript: transcript,
            topLeft: topLeft,
            topRight: topRight,
            bottomLeft: bottomLeft,
            bottomRight: bottomRight,
            in: viewSize
        )
        let rows = transcript.components(separatedBy: .newlines)
        guard rows.count > 1, abs(whole.angleDegrees) <= maxSplitAngleDegrees else { return [whole] }
        let rowHeight = whole.boundingBox.height / CGFloat(rows.count)
        return rows.enumerated().map { index, row in
            RecognizedTextLine(
                text: row,
                boundingBox: CGRect(
                    x: whole.boundingBox.minX,
                    y: whole.boundingBox.maxY - rowHeight * CGFloat(index + 1),
                    width: whole.boundingBox.width,
                    height: rowHeight
                ),
                angleDegrees: whole.angleDegrees
            )
        }
    }

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
        let angleDegrees = atan2(topLeft.y - topRight.y, topRight.x - topLeft.x) * 180 / .pi

        guard viewSize.width > 0, viewSize.height > 0 else {
            return RecognizedTextLine(text: transcript, boundingBox: .zero, angleDegrees: angleDegrees)
        }

        return RecognizedTextLine(
            text: transcript,
            boundingBox: CGRect(
                x: minX / viewSize.width,
                y: 1 - maxY / viewSize.height,
                width: (maxX - minX) / viewSize.width,
                height: (maxY - minY) / viewSize.height
            ),
            angleDegrees: angleDegrees
        )
    }
}
