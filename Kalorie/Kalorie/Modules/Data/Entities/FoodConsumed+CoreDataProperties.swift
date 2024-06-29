//
//  FoodConsumed+CoreDataProperties.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//
// swiftlint:disable implicit_return colon attributes

import Foundation
import CoreData


extension FoodConsumed {

    @nonobjc public class func fetchRequest() -> NSFetchRequest<FoodConsumed> {
        return NSFetchRequest<FoodConsumed>(entityName: "FoodConsumed")
    }

    @NSManaged public var calories: Int16
    @NSManaged public var date: TimeInterval
    @NSManaged public var id: String?
    @NSManaged public var name: String?
    @NSManaged public var weight: Double
    @NSManaged public var mealType: MealType?
}

extension FoodConsumed : Identifiable {
    
    var timeFormatted: String {
        Date(timeIntervalSince1970: date).formatDateStyle(with: "HH:mm")
    }
    
    var caloriesFormatted: String {
        "\(self.calories) kcal"
    }
}
