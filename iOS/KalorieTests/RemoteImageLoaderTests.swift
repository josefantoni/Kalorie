//
//  RemoteImageLoaderTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 09.10.2026.
//

import UIKit
import XCTest
@testable import Kalorie

final class RemoteImageLoaderTests: XCTestCase {
    func test_load_retriesAfterFailureAndReturnsTheImage() async throws {
        let (sut, fetcher) = makeSUT(failuresBeforeSuccess: 2)
        _ = try await sut.load(url)
        XCTAssertEqual(fetcher.callCount, 3)
    }

    func test_load_givesUpAfterMaxAttempts() async {
        let (sut, fetcher) = makeSUT(failuresBeforeSuccess: 10)
        do {
            _ = try await sut.load(url)
            XCTFail("Expected a failure")
        } catch {
            XCTAssertEqual(fetcher.callCount, 3)
        }
    }

    func test_load_doesNotRetryWhenCancelled() async {
        let fetcher = FetcherFake(failuresBeforeSuccess: 10, error: URLError(.cancelled))
        let sut = RemoteImageLoader(fetch: fetcher.fetch, maxAttempts: 3, retryDelay: .zero)
        do {
            _ = try await sut.load(url)
            XCTFail("Expected a failure")
        } catch {
            XCTAssertEqual(fetcher.callCount, 1)
        }
    }

    func test_load_failsOnUndecodableData() async {
        let fetcher = FetcherFake(failuresBeforeSuccess: 0, data: Data("not an image".utf8))
        let sut = RemoteImageLoader(fetch: fetcher.fetch, maxAttempts: 2, retryDelay: .zero)
        do {
            _ = try await sut.load(url)
            XCTFail("Expected a failure")
        } catch {
            XCTAssertEqual(fetcher.callCount, 2)
        }
    }

    // MARK: - Helpers

    private let url = URL(fileURLWithPath: "/photo.jpg")

    private func makeSUT(failuresBeforeSuccess: Int) -> (sut: RemoteImageLoader, fetcher: FetcherFake) {
        let fetcher = FetcherFake(failuresBeforeSuccess: failuresBeforeSuccess)
        return (RemoteImageLoader(fetch: fetcher.fetch, maxAttempts: 3, retryDelay: .zero), fetcher)
    }
}

private final class FetcherFake: @unchecked Sendable {
    private(set) var callCount = 0
    private let failuresBeforeSuccess: Int
    private let error: Error
    private let data: Data

    init(
        failuresBeforeSuccess: Int,
        error: Error = URLError(.timedOut),
        data: Data = UIGraphicsImageRenderer(size: CGSize(width: 2, height: 2)).jpegData(withCompressionQuality: 1) { _ in }
    ) {
        self.failuresBeforeSuccess = failuresBeforeSuccess
        self.error = error
        self.data = data
    }

    func fetch(_ url: URL) async throws -> Data {
        callCount += 1
        if callCount <= failuresBeforeSuccess { throw error }
        return data
    }
}
