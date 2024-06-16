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
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.timeZone = TimeZone.current
        formatter.locale = Locale(identifier: "en_US_POSIX")
        
        guard var startTime = formatter.date(from: "2024-06-12 05:00") else {
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
