//
//  SearchFoodExternallyUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.09.2026.
//

import XCTest
@testable import Kalorie

final class SearchFoodExternallyUseCaseTests: XCTestCase {

    // MARK: - Lifecycle

    override func tearDown() {
        URLProtocolStub.reset()
        super.tearDown()
    }

    // MARK: - Tests

    func test_search_withSuccessfulResponse_returnsMappedItems() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeSearchResponseData())]

        let items = try await sut(query: "mleko")

        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.id, "1234567890123")
    }

    func test_search_setsUserAgentHeader() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeSearchResponseData())]

        _ = try await sut(query: "mleko")

        XCTAssertEqual(URLProtocolStub.lastRequest?.value(forHTTPHeaderField: "User-Agent"), Constants.OpenFoodFacts.userAgent)
    }

    func test_search_withNonSuccessStatusCode_throwsServerErrorInsteadOfDecodeFailure() async throws {
        let sut = makeSUT()
        let response = try makeResponse(statusCode: 429)
        let data = Data("Too Many Requests".utf8)
        URLProtocolStub.stubbedResponseQueue = Array(repeating: (response, data), count: Constants.OpenFoodFacts.maxAttempts)

        do {
            _ = try await sut(query: "mleko")
            XCTFail("Expected SearchFoodExternallyError.serverError to be thrown")
        } catch SearchFoodExternallyError.serverError(let statusCode) {
            XCTAssertEqual(statusCode, 429)
        } catch {
            XCTFail("Expected SearchFoodExternallyError.serverError, got \(error)")
        }
        XCTAssertEqual(URLProtocolStub.requestCount, Constants.OpenFoodFacts.maxAttempts)
    }

    func test_search_withTransientServerErrorThenSuccess_retriesAndReturnsMappedItems() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedResponseQueue = [
            (try makeResponse(statusCode: 503), Data("Service Unavailable".utf8)),
            (try makeResponse(statusCode: 200), makeSearchResponseData())
        ]

        let items = try await sut(query: "mleko")

        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(URLProtocolStub.requestCount, 2)
    }

    func test_search_withTimedOutThenSuccess_retriesAndReturnsMappedItems() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = [URLError(.timedOut)]
        URLProtocolStub.stubbedResponseQueue = [(try makeResponse(statusCode: 200), makeSearchResponseData())]

        let items = try await sut(query: "mleko")

        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(URLProtocolStub.requestCount, 2)
    }

    func test_search_withPersistentNetworkConnectionLost_throwsAfterMaxAttempts() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = Array(repeating: URLError(.networkConnectionLost), count: Constants.OpenFoodFacts.maxAttempts)

        do {
            _ = try await sut(query: "mleko")
            XCTFail("Expected URLError to be thrown")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .networkConnectionLost)
        }
        XCTAssertEqual(URLProtocolStub.requestCount, Constants.OpenFoodFacts.maxAttempts)
    }

    func test_search_withNotConnectedToInternet_throwsWithoutRetrying() async throws {
        let sut = makeSUT()
        URLProtocolStub.stubbedErrorQueue = [URLError(.notConnectedToInternet)]

        do {
            _ = try await sut(query: "mleko")
            XCTFail("Expected URLError to be thrown")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        }
        XCTAssertEqual(URLProtocolStub.requestCount, 1)
    }

    // MARK: - Helpers

    private func makeSUT() -> SearchFoodExternallyUseCase {
        SearchFoodExternallyUseCase(session: URLProtocolStub.makeSession(), retryDelay: .zero)
    }

    private func makeResponse(statusCode: Int) throws -> HTTPURLResponse {
        let url = try XCTUnwrap(Constants.OpenFoodFacts.baseURL)
        return try XCTUnwrap(
            HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil)
        )
    }

    private func makeSearchResponseData() -> Data {
        Data(
            """
            {
                "products": [
                    {
                        "code": "1234567890123",
                        "product_name_cs": "Mléko",
                        "nutriments": { "energy-kcal_100g": 60 }
                    }
                ]
            }
            """.utf8
        )
    }
}
