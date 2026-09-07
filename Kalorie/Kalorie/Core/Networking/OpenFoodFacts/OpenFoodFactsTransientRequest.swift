//
//  OpenFoodFactsTransientRequest.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.09.2026.
//

import Foundation

enum OpenFoodFactsTransientRequest {

    // MARK: - Functions

    static func data(
        for request: URLRequest,
        session: URLSession,
        retryDelay: Duration
    ) async throws -> (data: Data, statusCode: Int) {
        var attempt = 1
        while true {
            do {
                let (data, response) = try await session.data(for: request)
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
                let isTransientFailure = statusCode == 429 || (500...599).contains(statusCode)
                guard isTransientFailure, attempt < Constants.OpenFoodFacts.maxAttempts else {
                    return (data, statusCode)
                }
            } catch let error as URLError {
                guard isTransient(error), attempt < Constants.OpenFoodFacts.maxAttempts else { throw error }
            }
            try await Task.sleep(for: retryDelay * attempt)
            attempt += 1
        }
    }

    private static func isTransient(_ error: URLError) -> Bool {
        switch error.code {
        case .timedOut, .networkConnectionLost:
            return true
        default:
            return false
        }
    }
}
