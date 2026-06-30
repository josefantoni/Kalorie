//
//  FoodConsumed+CoreDataClass.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//

import Foundation
import CoreData

public class FoodConsumed: NSManagedObject {
    
    // MARK: - Init

    convenience init(
        id: String,
        name: String,
        weight: Double,
        date: TimeInterval,
        calories: Int,
        mealType: MealType?,
        context: NSManagedObjectContext
    ) {
        self.init(context: context)

        self.id = id
        self.name = name
        self.weight = weight
        self.date = date
        self.calories = calories.toInt16
        self.mealType = mealType
    }
}
