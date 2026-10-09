//
//  RemoteImageLoader.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.10.2026.
//

import UIKit

struct RemoteImageLoader {

    // MARK: - Properties

    var fetch: (URL) async throws -> Data = { url in
        let (data, response) = try await URLSession.shared.data(from: url)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200..<300).contains(httpResponse.statusCode)
        else { throw URLError(.badServerResponse) }
        return data
    }
    var maxAttempts = 3
    var retryDelay: Duration = .milliseconds(500)

    // MARK: - Functions

    func load(_ url: URL) async throws -> UIImage {
        var lastError: Error = URLError(.unknown)
        for attempt in 1...maxAttempts {
            do {
                let data = try await fetch(url)
                guard let image = UIImage(data: data) else { throw URLError(.cannotDecodeContentData) }
                return image
            } catch {
                if error is CancellationError || (error as? URLError)?.code == .cancelled { throw error }
                lastError = error
                if attempt < maxAttempts { try await Task.sleep(for: retryDelay) }
            }
        }
        throw lastError
    }
}
