//
//  DeleteFoodPhotoUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.10.2026.
//

import XCTest
@testable import Kalorie

final class DeleteFoodPhotoUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_delete_removesTheFileBehindTheUrl() async throws {
        let (sut, storage) = makeSUT()
        let url = try await storage.uploadAsync(data: Data([1]), path: "submissionPhotos/alice/a.jpg", contentType: "image/jpeg")
        try await sut(url: url)
        XCTAssertEqual(storage.deletedURLs, [url])
        XCTAssertNil(storage.files[url])
    }

    func test_delete_whenStorageFails_throws() async throws {
        let (sut, storage) = makeSUT()
        storage.deleteError = URLError(.notConnectedToInternet)
        let url = try XCTUnwrap(URL(string: "https://storage.fake/a.jpg"))
        do {
            try await sut(url: url)
            XCTFail("Expected the storage error")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: DeleteFoodPhotoUseCase, storage: StorageDataProviderFake) {
        let storage = StorageDataProviderFake()
        return (DeleteFoodPhotoUseCase(storageProvider: storage), storage)
    }
}
