//
//  FoodItemReportDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

struct FoodItemReportDTO: Codable {

    // MARK: - Properties

    let barcode: String
    let reportedBy: String
    let reason: String
    let reportedAt: TimeInterval

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case barcode, reason
        case reportedBy = "reported_by"
        case reportedAt = "reported_at"
    }

    // MARK: - Init

    init(barcode: String, reportedBy: String, reason: String, reportedAt: Date) {
        self.barcode = barcode
        self.reportedBy = reportedBy
        self.reason = reason
        self.reportedAt = reportedAt.timeIntervalSince1970
    }

    // MARK: - Functions

    func asDomain() -> FoodItemReportDomain {
        FoodItemReportDomain(
            barcode: barcode,
            reportedBy: reportedBy,
            reason: reason,
            reportedAt: reportedAt.toDate
        )
    }
}
