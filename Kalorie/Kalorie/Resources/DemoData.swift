//
//  DemoData.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import CoreData


enum DemoData {
    
    // MARK: - Properties

    private static var defaultDate: Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.timeZone = TimeZone.current
        formatter.locale = Locale(identifier: "en_US_POSIX")
        
        guard let startTime = formatter.date(from: "2024-06-12 05:00") else {
            fatalError("No toto? Nějak se pokazilo vytváření dne v: 'DemoData.defaultDate'")
        }
        return startTime
    }

    
    // MARK: - Functions
    
    static func demoMeals(on context: NSManagedObjectContext) -> [MealType] {
        var startTime = defaultDate
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
    
    static func demofoodConsumed(on context: NSManagedObjectContext, date: Date = defaultDate) -> FoodConsumed {
        let foodConsumed = FoodConsumed(
            id: "12345",
            name: "Olomoucké tvarůžky",
            weight: 100.0,
            date: date.timeIntervalSince1970,
            calories: 100,
            mealType: nil,
            context: context
        )
        return foodConsumed
    }
}
