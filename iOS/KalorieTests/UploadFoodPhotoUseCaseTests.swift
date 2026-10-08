//
//  UploadFoodPhotoUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.10.2026.
//

import XCTest
@testable import Kalorie

final class UploadFoodPhotoUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_upload_storesTheBytesInTheGivenFolderAsJpeg() async throws {
        let (sut, storage) = makeSUT()
        let url = try await sut(data: Data([1, 2, 3]), folder: "submissionPhotos/alice")
        let path = try XCTUnwrap(storage.uploadedPaths.first)
        XCTAssertTrue(path.hasPrefix("submissionPhotos/alice/"))
        XCTAssertTrue(path.hasSuffix(".jpg"))
        XCTAssertEqual(storage.files[url], Data([1, 2, 3]))
    }

    func test_upload_neverReusesAFileName() async throws {
        let (sut, storage) = makeSUT()
        _ = try await sut(data: Data([1]), folder: "submissionPhotos/alice")
        _ = try await sut(data: Data([2]), folder: "submissionPhotos/alice")
        XCTAssertEqual(Set(storage.uploadedPaths).count, 2, "storage rules forbid overwriting, so every upload needs a fresh name")
    }

    func test_upload_whenStorageFails_throws() async {
        let (sut, storage) = makeSUT()
        storage.uploadError = URLError(.notConnectedToInternet)
        do {
            _ = try await sut(data: Data([1]), folder: "submissionPhotos/alice")
            XCTFail("Expected the storage error")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: UploadFoodPhotoUseCase, storage: StorageDataProviderFake) {
        let storage = StorageDataProviderFake()
        return (UploadFoodPhotoUseCase(storageProvider: storage), storage)
    }
}
