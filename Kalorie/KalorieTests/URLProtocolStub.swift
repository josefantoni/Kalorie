//
//  URLProtocolStub.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.09.2026.
//

import Foundation

enum URLProtocolStubError: Error {
    case queueExhausted
}

final class URLProtocolStub: URLProtocol {

    // MARK: - Properties

    static var stubbedResponseQueue: [(response: URLResponse, data: Data)] = []
    static var stubbedError: Error?
    static var stubbedErrorQueue: [Error] = []
    private static var requestLog: [URLRequest] = []

    static var lastRequest: URLRequest? { requestLog.last }
    static var requestCount: Int { requestLog.count }

    // MARK: - Functions

    static func makeSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [URLProtocolStub.self]
        return URLSession(configuration: configuration)
    }

    static func reset() {
        stubbedResponseQueue = []
        stubbedError = nil
        stubbedErrorQueue = []
        requestLog = []
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        URLProtocolStub.requestLog.append(request)
        if !URLProtocolStub.stubbedErrorQueue.isEmpty {
            let error = URLProtocolStub.stubbedErrorQueue.removeFirst()
            client?.urlProtocol(self, didFailWithError: error)
            return
        }
        if let error = URLProtocolStub.stubbedError {
            client?.urlProtocol(self, didFailWithError: error)
            return
        }
        guard !URLProtocolStub.stubbedResponseQueue.isEmpty else {
            client?.urlProtocol(self, didFailWithError: URLProtocolStubError.queueExhausted)
            return
        }
        let (response, data) = URLProtocolStub.stubbedResponseQueue.removeFirst()
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
