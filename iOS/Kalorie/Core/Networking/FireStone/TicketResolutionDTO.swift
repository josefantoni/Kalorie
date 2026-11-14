//
//  TicketResolutionDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

struct TicketResolutionDTO: Encodable {

    // MARK: - Properties

    let status: TicketStatus
    let response: String
    let resolvedAt: TimeInterval
    let hidden = false

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case status, response, hidden
        case resolvedAt = "resolved_at"
    }

    // MARK: - Init

    init(status: TicketStatus, response: String, resolvedAt: Date) {
        self.status = status
        self.response = response
        self.resolvedAt = resolvedAt.timeIntervalSince1970
    }
}
