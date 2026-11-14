//
//  FoodItemReportDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import FirebaseFirestore
import Foundation

struct FoodItemReportDTO: Decodable {

    // MARK: - Properties

    @DocumentID var id: String?
    let barcode: String
    let reportedBy: String?
    let reason: String
    let reportedAt: TimeInterval
    let status: TicketStatus
    let locale: String?
    let response: String?
    let resolvedAt: TimeInterval?
    let seenAt: TimeInterval?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id
        case barcode, reason, status, locale, response
        case reportedBy = "reported_by"
        case reportedAt = "reported_at"
        case resolvedAt = "resolved_at"
        case seenAt = "seen_at"
    }

    // MARK: - Functions

    func asDomain() -> FoodItemReportDomain? {
        guard let id else { return nil }
        return FoodItemReportDomain(
            id: id,
            barcode: barcode,
            reportedBy: reportedBy,
            reason: reason,
            reportedAt: reportedAt.toDate,
            status: status,
            locale: TicketLocale.resolved(locale),
            response: response,
            resolvedAt: resolvedAt?.toDate,
            seenAt: seenAt?.toDate
        )
    }
}

struct FoodItemReportCreateDTO: Encodable {

    // MARK: - Properties

    let barcode: String
    let reportedBy: String
    let reason: String
    let reportedAt: TimeInterval
    let status = TicketStatus.pending
    let locale: String
    let hidden = false

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case barcode, reason, status, locale, hidden
        case reportedBy = "reported_by"
        case reportedAt = "reported_at"
    }

    // MARK: - Init

    init(barcode: String, reportedBy: String, reason: String, reportedAt: Date, locale: String) {
        self.barcode = barcode
        self.reportedBy = reportedBy
        self.reason = reason
        self.reportedAt = reportedAt.timeIntervalSince1970
        self.locale = locale
    }
}
