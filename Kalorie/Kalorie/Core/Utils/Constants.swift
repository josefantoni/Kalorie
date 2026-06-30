//
//  Constants.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

enum Constants {

    enum CoreData {
        static let modelName = "Model"

        enum EntityName {
            static let mealType = "MealType"
            static let foodConsumed = "FoodConsumed"
        }
    }

    enum UserDefaultsKeys {
        static let firstOpen = "FirstOpen"
    }
}
