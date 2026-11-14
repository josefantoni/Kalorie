//
//  NotificationSeenDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

struct NotificationSeenDTO: Encodable {

    // MARK: - Properties

    let seenAt: TimeInterval

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case seenAt = "seen_at"
    }

    // MARK: - Init

    init(seenAt: Date) {
        self.seenAt = seenAt.timeIntervalSince1970
    }
}
