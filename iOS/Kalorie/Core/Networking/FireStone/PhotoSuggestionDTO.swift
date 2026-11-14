//
//  PhotoSuggestionDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import FirebaseFirestore
import Foundation

struct PhotoSuggestionDTO: Decodable {

    // MARK: - Properties

    @DocumentID var id: String?
    let itemId: String
    let suggestedBy: String?
    let photoUrl: String
    let suggestedAt: TimeInterval
    let status: TicketStatus
    let locale: String?
    let response: String?
    let resolvedAt: TimeInterval?
    let seenAt: TimeInterval?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id
        case status, locale, response
        case itemId = "item_id"
        case suggestedBy = "suggested_by"
        case photoUrl = "photo_url"
        case suggestedAt = "suggested_at"
        case resolvedAt = "resolved_at"
        case seenAt = "seen_at"
    }

    // MARK: - Functions

    func asDomain() -> PhotoSuggestionDomain? {
        guard
            let id,
            let photoURL = URL(string: photoUrl)
        else { return nil }
        return PhotoSuggestionDomain(
            id: id,
            itemId: itemId,
            suggestedBy: suggestedBy,
            photoURL: photoURL,
            suggestedAt: suggestedAt.toDate,
            status: status,
            locale: TicketLocale.resolved(locale),
            response: response,
            resolvedAt: resolvedAt?.toDate,
            seenAt: seenAt?.toDate
        )
    }
}

struct PhotoSuggestionCreateDTO: Encodable {

    // MARK: - Properties

    let itemId: String
    let suggestedBy: String
    let photoUrl: String
    let suggestedAt: TimeInterval
    let status = TicketStatus.pending
    let locale: String
    let hidden = false

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case status, locale, hidden
        case itemId = "item_id"
        case suggestedBy = "suggested_by"
        case photoUrl = "photo_url"
        case suggestedAt = "suggested_at"
    }

    // MARK: - Init

    init(itemId: String, suggestedBy: String, photoURL: URL, suggestedAt: Date, locale: String) {
        self.itemId = itemId
        self.suggestedBy = suggestedBy
        self.photoUrl = photoURL.absoluteString
        self.suggestedAt = suggestedAt.timeIntervalSince1970
        self.locale = locale
    }
}
