//
//  FoodPhotoProcessing.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import UIKit

enum FoodPhotoProcessingError: Error, Equatable {
    case undecodable
    case encodingFailed
}

enum FoodPhotoProcessing {

    // MARK: - Properties

    static let maxSide: CGFloat = 1080
    static let jpegQuality: CGFloat = 0.7

    // MARK: - Functions

    static func process(_ data: Data) throws -> Data {
        guard let image = UIImage(data: data) else { throw FoodPhotoProcessingError.undecodable }
        let side = min(image.size.width, image.size.height)
        guard side > 0 else { throw FoodPhotoProcessingError.undecodable }
        let target = min(side, maxSide)
        let scale = target / side
        let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let origin = CGPoint(x: (target - drawSize.width) / 2, y: (target - drawSize.height) / 2)

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let squared = UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: format).image { _ in
            image.draw(in: CGRect(origin: origin, size: drawSize))
        }
        guard let jpeg = squared.jpegData(compressionQuality: jpegQuality) else { throw FoodPhotoProcessingError.encodingFailed }
        return jpeg
    }
}
