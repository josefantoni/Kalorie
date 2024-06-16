//
//  DemoData.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import CoreData


enum DemoData {
    
    static func demoMeals(on context: NSManagedObjectContext) -> [MealType] {
        guard var startTime = Calendar.current.date(bySettingHour: 5, minute: 0, second: 0, of: Date.now) else {
            fatalError("No toto? Nějak se pokazilo vytváření dne v: 'demoMeals'")
        }
        var endTime = startTime.withAddedHours(hours: 3)
        var id = 0
        let mealNames = ["Snídaně", "Druhá snídaně", "Oběd", "Svačina", "Večeře"]
        var mealTypes: [MealType] = []
        
        for mealName in mealNames {
            let meal = MealType(
                id: id,
                name: mealName,
                startTime: startTime,
                endTime: endTime,
                context: context
            )
            startTime = endTime
            endTime = startTime.withAddedHours(hours: 3)
            id += 1
            mealTypes.append(meal)
        }
        return mealTypes
    }
}
