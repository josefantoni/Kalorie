//
//  StorageDataProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import Foundation
import FirebaseStorage
import OSLog

protocol StorageDataProviderProtocol {
    func uploadAsync(data: Data, path: String, contentType: String) async throws -> URL
    func downloadAsync(url: URL) async throws -> Data
    func deleteAsync(url: URL) async throws
    func listAsync(prefix: String) async throws -> [URL]
}

struct StorageDataProvider: StorageDataProviderProtocol {

    // MARK: - Functions

    func uploadAsync(data: Data, path: String, contentType: String) async throws -> URL {
        log("🚀 PUT \(path) (\(data.count) bytes)")
        do {
            let reference = Storage.storage().reference(withPath: path)
            let metadata = StorageMetadata()
            metadata.contentType = contentType
            metadata.cacheControl = Constants.Storage.photoCacheControl
            _ = try await reference.putDataAsync(data, metadata: metadata)
            let url = try await reference.downloadURL()
            log("✅ PUT \(path)")
            return url.removingExplicitPort()
        } catch {
            logFailure("❌ PUT \(path)", error: error)
            throw error
        }
    }

    func downloadAsync(url: URL) async throws -> Data {
        log("🚀 GET \(url.absoluteString)")
        do {
            let data = try await Storage.storage().reference(forURL: url.absoluteString).data(maxSize: Constants.Storage.maxDownloadBytes)
            log("✅ GET \(url.absoluteString) → \(data.count) bytes")
            return data
        } catch {
            logFailure("❌ GET \(url.absoluteString)", error: error)
            throw error
        }
    }

    func deleteAsync(url: URL) async throws {
        log("🚀 DELETE \(url.absoluteString)")
        do {
            try await Storage.storage().reference(forURL: url.absoluteString).delete()
            log("✅ DELETE \(url.absoluteString)")
        } catch {
            logFailure("❌ DELETE \(url.absoluteString)", error: error)
            throw error
        }
    }

    func listAsync(prefix: String) async throws -> [URL] {
        log("🚀 LIST \(prefix)")
        do {
            let result = try await Storage.storage().reference(withPath: prefix).listAll()
            var urls: [URL] = []
            for item in result.items {
                urls.append(try await item.downloadURL())
            }
            log("✅ LIST \(prefix) → \(urls.count) items")
            return urls
        } catch {
            logFailure("❌ LIST \(prefix)", error: error)
            throw error
        }
    }
}

#if DEBUG
final class StorageDataProviderFake: StorageDataProviderProtocol {

    // MARK: - Properties

    var files: [URL: Data] = [:]
    var uploadError: Error?
    var downloadError: Error?
    var deleteError: Error?
    var listError: Error?
    private(set) var uploadedPaths: [String] = []
    private(set) var deletedURLs: [URL] = []

    // MARK: - Functions

    func uploadAsync(data: Data, path: String, contentType: String) async throws -> URL {
        if let uploadError { throw uploadError }
        guard let url = URL(string: "https://storage.fake/\(path.replacingOccurrences(of: "/", with: "%2F"))") else {
            throw URLError(.badURL)
        }
        files[url] = data
        uploadedPaths.append(path)
        return url
    }

    func downloadAsync(url: URL) async throws -> Data {
        if let downloadError { throw downloadError }
        guard let data = files[url] else { throw URLError(.fileDoesNotExist) }
        return data
    }

    func deleteAsync(url: URL) async throws {
        if let deleteError { throw deleteError }
        files[url] = nil
        deletedURLs.append(url)
    }

    func listAsync(prefix: String) async throws -> [URL] {
        if let listError { throw listError }
        let encodedPrefix = prefix.replacingOccurrences(of: "/", with: "%2F")
        return files.keys
            .filter { $0.absoluteString.hasPrefix("https://storage.fake/\(encodedPrefix)") }
            .sorted { $0.absoluteString < $1.absoluteString }
    }
}
#endif

private let logger = Log.logger(Constants.LogCategory.storage)

private func log(_ message: String) {
    #if DEBUG
    print(message)
    #endif
}

private func logFailure(_ message: String, error: Error) {
    logger.error("\(message): \(String(describing: error), privacy: .public)")
}

extension URL {
    func removingExplicitPort() -> URL {
        guard var components = URLComponents(url: self, resolvingAgainstBaseURL: false) else { return self }
        components.port = nil
        return components.url ?? self
    }
}
