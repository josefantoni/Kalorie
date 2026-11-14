//
//  PhotoSuggestionRemover.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

struct PhotoSuggestionRemover {

    // MARK: - Properties

    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(storageProvider: any StorageDataProviderProtocol) {
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func deleteFile(at photoURL: URL) async throws {
        try await storageProvider.deleteIgnoringMissing(url: photoURL, category: Constants.LogCategory.moderation)
    }

    func deleteFileQuietly(at photoURL: URL) async {
        do {
            try await deleteFile(at: photoURL)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
        }
    }
}
