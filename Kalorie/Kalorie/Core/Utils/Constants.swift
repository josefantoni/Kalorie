//
//  Constants.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

enum Constants {

    enum Time {
        static let secondsPerDay: TimeInterval = 24 * 60 * 60
    }

    enum Firestore {
        static let foodItems = "foodItems"
        static func mealTypes(userId: String) -> String { "users/\(userId)/mealTypes" }
        static func foodConsumed(userId: String) -> String { "users/\(userId)/foodConsumed" }
    }

}
