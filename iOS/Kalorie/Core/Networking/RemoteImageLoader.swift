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
    var cache: NSCache<NSURL, UIImage> = RemoteImageLoader.sharedCache
    var store: RemoteImageStore? = .shared

    private static let sharedCache = NSCache<NSURL, UIImage>()

    // MARK: - Functions

    func cachedImage(for url: URL) -> UIImage? {
        if let image = cache.object(forKey: url as NSURL) { return image }
        guard
            let data = store?.data(for: url),
            let image = UIImage(data: data)
        else { return nil }
        cache.setObject(image, forKey: url as NSURL)
        return image
    }

    func load(_ url: URL) async throws -> UIImage {
        if let cached = cachedImage(for: url) { return cached }
        var lastError: Error = URLError(.unknown)
        for attempt in 1...maxAttempts {
            do {
                let data = try await fetch(url)
                guard let image = UIImage(data: data) else { throw URLError(.cannotDecodeContentData) }
                cache.setObject(image, forKey: url as NSURL)
                store?.save(data, for: url)
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
