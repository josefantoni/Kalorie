//
//  PhotoSuggestionRemover.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

struct PhotoSuggestionRemover {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, storageProvider: any StorageDataProviderProtocol) {
        self.dataProvider = dataProvider
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func remove(itemId: String, userId: String) async throws {
        // A file that is already gone must not keep the document, and so the queue row, alive forever.
        // Any other failure keeps the document so the removal can be retried instead of orphaning the file.
        try await storageProvider.deleteIgnoringMissing(
            path: PhotoSuggestionDomain.storagePath(itemId: itemId, userId: userId),
            category: Constants.LogCategory.moderation
        )
        try await dataProvider.deleteAsync(
            id: PhotoSuggestionDomain.id(itemId: itemId, userId: userId),
            from: Constants.Firestore.photoSuggestions
        )
    }
}
