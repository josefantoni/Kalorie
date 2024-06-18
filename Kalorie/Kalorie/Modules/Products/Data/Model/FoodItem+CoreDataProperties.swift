//
//  FoodItem+CoreDataProperties.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//
// swiftlint:disable implicit_return colon attributes

import Foundation
import CoreData


extension FoodItem {

    @nonobjc public class func fetchRequest() -> NSFetchRequest<FoodItem> {
        return NSFetchRequest<FoodItem>(entityName: "FoodItem")
    }

    @NSManaged public var calories: Int16
    @NSManaged public var date: TimeInterval
    @NSManaged public var id: UUID?
    @NSManaged public var name: String?
    @NSManaged public var weight: Double
    @NSManaged public var mealType: MealType?
}

extension FoodItem : Identifiable {

}
