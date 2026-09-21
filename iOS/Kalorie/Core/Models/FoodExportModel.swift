//
//  FoodExportModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

enum FoodExportFormat: Hashable, CaseIterable {
    case pdf
    case xlsx

    // MARK: - Properties

    var fileExtension: String {
        switch self {
        case .pdf: "pdf"
        case .xlsx: "xlsx"
        }
    }
}
