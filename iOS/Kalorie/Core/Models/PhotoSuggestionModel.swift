//
//  PhotoSuggestionModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

struct PhotoSuggestionDomain: Equatable, Identifiable {

    // MARK: - Properties

    let id: String
    let itemId: String
    let suggestedBy: String?
    let photoURL: URL
    let suggestedAt: Date
    let status: TicketStatus
    let locale: String
    var response: String?
    var resolvedAt: Date?
    var seenAt: Date?
}
