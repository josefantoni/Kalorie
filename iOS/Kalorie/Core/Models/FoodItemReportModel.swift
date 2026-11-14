//
//  FoodItemReportModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

struct FoodItemReportDomain: Equatable, Identifiable {

    // MARK: - Properties

    let id: String
    let barcode: String
    let reportedBy: String?
    let reason: String
    let reportedAt: Date
    let status: TicketStatus
    let locale: String
    var response: String?
    var resolvedAt: Date?
    var seenAt: Date?
}

enum FoodItemReportError: Error {
    case reasonRequired
    case reasonTooLong
}
