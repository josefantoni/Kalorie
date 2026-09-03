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

    enum Auth {
        static let recentLoginThreshold: TimeInterval = 4 * 60
    }

    enum OpenFoodFacts {
        static let host = "world.openfoodfacts.org"
        static let baseURL = URL(string: "https://\(host)")
    }

    enum LogCategory {
        static let firestore = "firestore"
        static let auth = "auth"
        static let account = "account"
        static let favourites = "favourites"
        static let foodQuantity = "foodQuantity"
        static let mealTypeSheet = "mealTypeSheet"
        static let addFoodSheet = "addFoodSheet"
        static let dashboard = "dashboard"
        static let myCreatedMeal = "myCreatedMeal"
    }

    enum Firestore {
        static let foodItems = "foodItems"
        static let users = "users"
        static let batchWriteLimit = 500
        static func mealTypes(userId: String) -> String { "users/\(userId)/mealTypes" }
        static func foodConsumed(userId: String) -> String { "users/\(userId)/foodConsumed" }
        static func favouriteFoods(userId: String) -> String { "users/\(userId)/favouriteFoods" }
        static func myCreatedMeals(userId: String) -> String { "users/\(userId)/myCreatedMeals" }
    }
}
