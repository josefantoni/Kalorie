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
    @NSManaged public var foodConsumed: NSSet?
}

// MARK: Generated accessors for foodConsumed
extension MealType {

    @objc(addfoodConsumedObject:)
    @NSManaged public func addToFoodConsumed(_ value: FoodConsumed)

    @objc(removefoodConsumedObject:)
    @NSManaged public func removeFromFoodConsumed(_ value: FoodConsumed)

    @objc(addfoodConsumed:)
    @NSManaged public func addToFoodConsumed(_ values: NSSet)

    @objc(removefoodConsumed:)
    @NSManaged public func removeFromFoodConsumed(_ values: NSSet)
}

extension MealType : Identifiable {

}
