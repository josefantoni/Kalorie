//
//  FetchFoodByBarcodeExternallyUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodByBarcodeExternallyUseCaseTests: XCTestCase {

    // MARK: - Lifecycle

    override func tearDown() {
        URLProtocolStub.reset()
        super.tearDown()
    }

    // MARK: - Tests

    func test_fetchByBarcode_withEmptyBarcode_returnsNilWithoutRequest() async throws {
        let sut = makeSUT()

        let result = try await sut(barcode: "")

        XCTAssertNil(result)
        XCTAssertNil(URLProtocolStub.lastRequest)
    }

    func test_fetchByBarcode_withSuccessfulResponse_returnsMappedItem() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeBarcodeResponseData(status: 1))]

        let result = try await sut(barcode: "1234567890123")

        XCTAssertEqual(result?.id, "1234567890123")
    }

    func test_fetchByBarcode_withProductNotFoundStatus_returnsNil() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeBarcodeResponseData(status: 0))]

        let result = try await sut(barcode: "1234567890123")

        XCTAssertNil(result)
    }

    func test_fetchByBarcode_setsUserAgentHeader() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeBarcodeResponseData(status: 1))]

        _ = try await sut(barcode: "1234567890123")

        XCTAssertEqual(URLProtocolStub.lastRequest?.value(forHTTPHeaderField: "User-Agent"), Constants.OpenFoodFacts.userAgent)
    }

    func test_fetchByBarcode_withNonSuccessStatusCode_throwsServerErrorInsteadOfLookingLikeNotFound() async throws {
        let sut = makeSUT()
        let response = try makeResponse(statusCode: 500)
        let data = Data("Internal Server Error".utf8)
        URLProtocolStub.stubbedResponseQueue = Array(repeating: (response, data), count: Constants.OpenFoodFacts.maxAttempts)

        do {
            _ = try await sut(barcode: "1234567890123")
            XCTFail("Expected FetchFoodByBarcodeExternallyError.serverError to be thrown")
        } catch FetchFoodByBarcodeExternallyError.serverError(let statusCode) {
            XCTAssertEqual(statusCode, 500)
        } catch {
            XCTFail("Expected FetchFoodByBarcodeExternallyError.serverError, got \(error)")
        }
        XCTAssertEqual(URLProtocolStub.requestCount, Constants.OpenFoodFacts.maxAttempts)
    }

    func test_fetchByBarcode_withTransientServerErrorThenSuccess_retriesAndReturnsMappedItem() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [
            (try makeResponse(statusCode: 429), Data("Too Many Requests".utf8)),
            (try makeResponse(statusCode: 200), makeBarcodeResponseData(status: 1))
        ]

        let result = try await sut(barcode: "1234567890123")

        XCTAssertEqual(result?.id, "1234567890123")
        XCTAssertEqual(URLProtocolStub.requestCount, 2)
    }

    func test_fetchByBarcode_withTimedOutThenSuccess_retriesAndReturnsMappedItem() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = [URLError(.timedOut)]
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeBarcodeResponseData(status: 1))]

        let result = try await sut(barcode: "1234567890123")

        XCTAssertEqual(result?.id, "1234567890123")
        XCTAssertEqual(URLProtocolStub.requestCount, 2)
    }

    func test_fetchByBarcode_withPersistentNetworkConnectionLost_throwsAfterMaxAttempts() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = Array(repeating: URLError(.networkConnectionLost), count: Constants.OpenFoodFacts.maxAttempts)

        do {
            _ = try await sut(barcode: "1234567890123")
            XCTFail("Expected URLError to be thrown")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .networkConnectionLost)
        }
        XCTAssertEqual(URLProtocolStub.requestCount, Constants.OpenFoodFacts.maxAttempts)
    }

    func test_fetchByBarcode_withNotConnectedToInternet_throwsWithoutRetrying() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = [URLError(.notConnectedToInternet)]

        do {
            _ = try await sut(barcode: "1234567890123")
            XCTFail("Expected URLError to be thrown")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        }
        XCTAssertEqual(URLProtocolStub.requestCount, 1)
    }

    // MARK: - Helpers

    private func makeSUT() -> FetchFoodByBarcodeExternallyUseCase {
        FetchFoodByBarcodeExternallyUseCase(session: URLProtocolStub.makeSession(), retryDelay: .zero)
    }

    private func makeResponse(statusCode: Int) throws -> HTTPURLResponse {
        let url = try XCTUnwrap(Constants.OpenFoodFacts.baseURL)
        return try XCTUnwrap(
            HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil)
        )
    }

    private func makeBarcodeResponseData(status: Int) -> Data {
        Data(
            """
            {
                "status": \(status),
                "product": {
                    "code": "1234567890123",
                    "product_name_cs": "Mléko",
                    "nutriments": { "energy-kcal_100g": 60 }
                }
            }
            """.utf8
        )
    }
}
