//
//  FoodItemReportModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

struct FoodItemReportDomain: Equatable {

    // MARK: - Properties

    let barcode: String
    let reportedBy: String
    let reason: String
    let reportedAt: Date

    // MARK: - Functions

    static func id(barcode: String, userId: String) -> String {
        "\(barcode)_\(userId)"
    }
}

enum FoodItemReportError: Error {
    case reasonRequired
    case reasonTooLong
}
