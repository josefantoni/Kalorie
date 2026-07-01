//
//  Constants.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

enum Constants {

    enum Firestore {
        static let foodItems = "food_items"
        static func mealTypes(userId: String) -> String { "users/\(userId)/mealTypes" }
        static func foodConsumed(userId: String) -> String { "users/\(userId)/foodConsumed" }
    }

}
