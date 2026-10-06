//
//  FoodFrequencyModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import Foundation

struct FoodFrequencyEntry: Equatable {

    // MARK: - Properties

    let count: Int
    let lastLoggedAt: Date
    let item: FoodItemDomain
}
