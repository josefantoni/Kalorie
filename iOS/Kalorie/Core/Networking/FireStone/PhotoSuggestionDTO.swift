//
//  PhotoSuggestionDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

struct PhotoSuggestionDTO: Codable {

    // MARK: - Properties

    let itemId: String
    let suggestedBy: String
    let photoUrl: String
    let suggestedAt: TimeInterval

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case itemId = "item_id"
        case suggestedBy = "suggested_by"
        case photoUrl = "photo_url"
        case suggestedAt = "suggested_at"
    }

    // MARK: - Init

    init(itemId: String, suggestedBy: String, photoURL: URL, suggestedAt: Date) {
        self.itemId = itemId
        self.suggestedBy = suggestedBy
        self.photoUrl = photoURL.absoluteString
        self.suggestedAt = suggestedAt.timeIntervalSince1970
    }

    // MARK: - Functions

    func asDomain() -> PhotoSuggestionDomain? {
        guard let photoURL = URL(string: photoUrl) else { return nil }
        return PhotoSuggestionDomain(
            itemId: itemId,
            suggestedBy: suggestedBy,
            photoURL: photoURL,
            suggestedAt: suggestedAt.toDate
        )
    }
}
