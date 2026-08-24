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

    enum OpenFoodFacts {
        static let host = "world.openfoodfacts.org"
        static let baseURL = URL(string: "https://\(host)")
    }

    enum Firestore {
        static let foodItems = "foodItems"
        static let users = "users"
        static func mealTypes(userId: String) -> String { "users/\(userId)/mealTypes" }
        static func foodConsumed(userId: String) -> String { "users/\(userId)/foodConsumed" }
        static func favouriteFoods(userId: String) -> String { "users/\(userId)/favouriteFoods" }
        static func myCreatedMeals(userId: String) -> String { "users/\(userId)/myCreatedMeals" }
    }
}
