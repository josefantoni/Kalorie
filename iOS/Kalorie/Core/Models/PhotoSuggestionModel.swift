//
//  PhotoSuggestionModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

struct PhotoSuggestionDomain: Equatable, Identifiable {

    // MARK: - Properties

    let itemId: String
    let suggestedBy: String
    let photoURL: URL
    let suggestedAt: Date

    var id: String { Self.id(itemId: itemId, userId: suggestedBy) }

    // MARK: - Functions

    static func id(itemId: String, userId: String) -> String {
        "\(itemId)_\(userId)"
    }

    static func storagePath(itemId: String, userId: String) -> String {
        "\(Constants.Storage.photoSuggestionsFolder)/\(userId)/\(itemId).jpg"
    }

    static func ids(fromDocumentId documentId: String) -> (itemId: String, userId: String)? {
        guard let separator = documentId.firstIndex(of: "_") else { return nil }
        let itemId = String(documentId[..<separator])
        let userId = String(documentId[documentId.index(after: separator)...])
        guard !itemId.isEmpty, !userId.isEmpty else { return nil }
        return (itemId, userId)
    }
}
