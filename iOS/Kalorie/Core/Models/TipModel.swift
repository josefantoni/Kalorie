//
//  TipModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

struct TipProductDomain: Equatable, Identifiable {

    // MARK: - Properties

    let id: String
    let displayPrice: String
}

enum TipPurchaseOutcome: Equatable {
    case purchased
    case cancelled
    case pending
}
