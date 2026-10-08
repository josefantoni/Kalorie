//
//  StorageDataProviderFakeTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.10.2026.
//

import XCTest
@testable import Kalorie

final class StorageDataProviderFakeTests: XCTestCase {

    // MARK: - Tests

    func test_upload_thenDownload_returnsTheSameBytes() async throws {
        let sut = StorageDataProviderFake()
        let url = try await sut.uploadAsync(data: Data([1, 2, 3]), path: "submissionPhotos/alice/a.jpg", contentType: "image/jpeg")
        let data = try await sut.downloadAsync(url: url)
        XCTAssertEqual(data, Data([1, 2, 3]))
    }

    func test_list_returnsOnlyFilesUnderThePrefix() async throws {
        let sut = StorageDataProviderFake()
        let own = try await sut.uploadAsync(data: Data([1]), path: "submissionPhotos/alice/a.jpg", contentType: "image/jpeg")
        _ = try await sut.uploadAsync(data: Data([2]), path: "submissionPhotos/bob/b.jpg", contentType: "image/jpeg")
        _ = try await sut.uploadAsync(data: Data([3]), path: "catalogPhotos/1/c.jpg", contentType: "image/jpeg")
        let urls = try await sut.listAsync(prefix: "submissionPhotos/alice")
        XCTAssertEqual(urls, [own])
    }

    func test_delete_removesTheFile() async throws {
        let sut = StorageDataProviderFake()
        let url = try await sut.uploadAsync(data: Data([1]), path: "submissionPhotos/alice/a.jpg", contentType: "image/jpeg")
        try await sut.deleteAsync(url: url)
        let urls = try await sut.listAsync(prefix: "submissionPhotos/alice")
        XCTAssertTrue(urls.isEmpty)
        XCTAssertEqual(sut.deletedURLs, [url])
    }
}
