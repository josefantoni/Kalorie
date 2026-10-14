//
//  RemoteImageStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.10.2026.
//

import CryptoKit
import Foundation

struct RemoteImageStore {

    // MARK: - Properties

    let directory: URL
    var maxBytes = Constants.Storage.photoStoreMaxBytes

    static let shared = RemoteImageStore(
        directory: (FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first ?? FileManager.default.temporaryDirectory)
            .appendingPathComponent("RemotePhotos", isDirectory: true)
    )

    // MARK: - Functions

    func data(for url: URL) -> Data? {
        try? Data(contentsOf: fileURL(for: url))
    }

    func save(_ data: Data, for url: URL) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try data.write(to: fileURL(for: url), options: .atomic)
            prune()
        } catch {
            Log.warning(error, category: Constants.LogCategory.storage)
        }
    }

    private func fileURL(for url: URL) -> URL {
        let name = SHA256.hash(data: Data(url.absoluteString.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
        return directory.appendingPathComponent(name)
    }

    private func prune() {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys) else { return }
        var entries = files
            .compactMap { file -> (url: URL, size: Int, date: Date)? in
                guard
                    let values = try? file.resourceValues(forKeys: Set(keys)),
                    let size = values.fileSize,
                    let date = values.contentModificationDate
                else { return nil }
                return (file, size, date)
            }
            .sorted { $0.date < $1.date }
        var total = entries.reduce(0) { $0 + $1.size }
        while total > maxBytes, let oldest = entries.first {
            try? FileManager.default.removeItem(at: oldest.url)
            total -= oldest.size
            entries.removeFirst()
        }
    }
}
