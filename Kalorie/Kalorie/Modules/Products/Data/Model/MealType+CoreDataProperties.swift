//
//  MealType+CoreDataProperties.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//
// swiftlint:disable implicit_return colon attributes

import Foundation
import CoreData


extension MealType {

    @nonobjc public class func fetchRequest() -> NSFetchRequest<MealType> {
        return NSFetchRequest<MealType>(entityName: "MealType")
    }

    @NSManaged public var endTime: TimeInterval
    @NSManaged public var id: Int16
    @NSManaged public var name: String?
    @NSManaged public var startTime: TimeInterval
    @NSManaged public var foodItem: NSSet?
}

// MARK: Generated accessors for foodItem
extension MealType {

    @objc(addFoodItemObject:)
    @NSManaged public func addToFoodItem(_ value: FoodItem)

    @objc(removeFoodItemObject:)
    @NSManaged public func removeFromFoodItem(_ value: FoodItem)

    @objc(addFoodItem:)
    @NSManaged public func addToFoodItem(_ values: NSSet)

    @objc(removeFoodItem:)
    @NSManaged public func removeFromFoodItem(_ values: NSSet)
}

extension MealType : Identifiable {

}
