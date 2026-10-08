//
//  FoodPhotoProcessingTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.10.2026.
//

import ImageIO
import UniformTypeIdentifiers
import UIKit
import XCTest
@testable import Kalorie

final class FoodPhotoProcessingTests: XCTestCase {

    // MARK: - Tests

    func test_process_cropsToASquareOfTheShorterEdge() throws {
        let output = try FoodPhotoProcessing.process(try makeJPEG(width: 600, height: 200))
        XCTAssertEqual(try pixelSize(of: output), CGSize(width: 200, height: 200))
    }

    func test_process_downscalesALargeImageTo1080() throws {
        let output = try FoodPhotoProcessing.process(try makeJPEG(width: 3000, height: 2000))
        XCTAssertEqual(try pixelSize(of: output), CGSize(width: 1080, height: 1080))
    }

    func test_process_neverUpscalesASmallImage() throws {
        let output = try FoodPhotoProcessing.process(try makeJPEG(width: 120, height: 120))
        XCTAssertEqual(try pixelSize(of: output), CGSize(width: 120, height: 120))
    }

    func test_process_outputIsJPEG() throws {
        let output = try FoodPhotoProcessing.process(try makeJPEG(width: 300, height: 300))
        XCTAssertEqual(Array(output.prefix(2)), [0xFF, 0xD8])
    }

    func test_process_dropsGPSAndIdentifyingMetadata() throws {
        let source = try makeJPEG(width: 300, height: 300, withGPS: true)
        XCTAssertNotNil(try properties(of: source)[kCGImagePropertyGPSDictionary], "the fixture must carry GPS, or this test proves nothing")
        let properties = try properties(of: FoodPhotoProcessing.process(source))
        XCTAssertNil(properties[kCGImagePropertyGPSDictionary], "GPS must never be uploaded")
        let exifKeys = Set((properties[kCGImagePropertyExifDictionary] as? [CFString: Any] ?? [:]).keys)
        let technicalKeys: Set<CFString> = [kCGImagePropertyExifColorSpace, kCGImagePropertyExifPixelXDimension, kCGImagePropertyExifPixelYDimension]
        XCTAssertTrue(exifKeys.isSubset(of: technicalKeys), "only technical keys may remain, found \(exifKeys)")
        let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any] ?? [:]
        XCTAssertNil(tiff[kCGImagePropertyTIFFMake])
        XCTAssertNil(tiff[kCGImagePropertyTIFFModel])
        XCTAssertNil(tiff[kCGImagePropertyTIFFDateTime])
    }

    func test_process_appliesExifOrientationBeforeCropping() throws {
        let source = try makeJPEG(width: 400, height: 200, leftHalf: .red, rightHalf: .blue, orientation: 6)
        let output = try FoodPhotoProcessing.process(source)
        let bottomLeft = try pixel(of: output, column: 20, row: 150)
        XCTAssertGreaterThan(bottomLeft.blue, bottomLeft.red, "orientation 6 turns the blue right half to the bottom")
    }

    func test_process_whenDataIsNotAnImage_throws() {
        XCTAssertThrowsError(try FoodPhotoProcessing.process(Data([1, 2, 3]))) {
            XCTAssertEqual($0 as? FoodPhotoProcessingError, .undecodable)
        }
    }

    // MARK: - Helpers

    private func makeJPEG(
        width: Int,
        height: Int,
        leftHalf: UIColor = .red,
        rightHalf: UIColor = .red,
        orientation: Int = 1,
        withGPS: Bool = false
    ) throws -> Data {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            leftHalf.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width / 2, height: height))
            rightHalf.setFill()
            context.fill(CGRect(x: width / 2, y: 0, width: width - width / 2, height: height))
        }
        let data = NSMutableData()
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil))
        var metadata: [CFString: Any] = [kCGImagePropertyOrientation: orientation]
        if withGPS {
            metadata[kCGImagePropertyGPSDictionary] = [
                kCGImagePropertyGPSLatitude: 50.08,
                kCGImagePropertyGPSLatitudeRef: "N",
                kCGImagePropertyGPSLongitude: 14.42,
                kCGImagePropertyGPSLongitudeRef: "E"
            ]
        }
        CGImageDestinationAddImage(destination, try XCTUnwrap(image.cgImage), metadata as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return data as Data
    }

    private func properties(of data: Data) throws -> [CFString: Any] {
        let source = try XCTUnwrap(CGImageSourceCreateWithData(data as CFData, nil))
        return CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] ?? [:]
    }

    private func pixelSize(of data: Data) throws -> CGSize {
        let image = try XCTUnwrap(UIImage(data: data)?.cgImage)
        return CGSize(width: image.width, height: image.height)
    }

    private func pixel(of data: Data, column: Int, row: Int) throws -> (red: Int, blue: Int) {
        let image = try XCTUnwrap(UIImage(data: data)?.cgImage)
        var bytes = [UInt8](repeating: 0, count: 4)
        let context = try XCTUnwrap(CGContext(
            data: &bytes,
            width: 1,
            height: 1,
            bitsPerComponent: 8,
            bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(image, in: CGRect(x: -column, y: -(image.height - 1 - row), width: image.width, height: image.height))
        return (Int(bytes[0]), Int(bytes[2]))
    }
}
