//
//  MealTypeDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import MealKit

struct MealTypeDomain {

    // MARK: - Properties

    let id: String
    let name: String
    let startTime: Date
    let endTime: Date
}

extension [MealTypeDomain] {
    func mealType(at date: Date) -> MealTypeDomain? {
        let minutes = date.minutesSinceMidnight
        return sorted { $0.startTime < $1.startTime }.first {
            MealWindowsKt.isMinuteWithinWindow(
                minutes: minutes,
                startMinutes: $0.startTime.minutesSinceMidnight,
                endMinutes: $0.endTime.minutesSinceMidnight
            )
        }
    }

    func resolvedMealTypeId(for food: FoodConsumedDomain) -> String? {
        if
            let pinnedMealTypeId = food.mealTypeId,
            contains(where: { $0.id == pinnedMealTypeId })
        {
            return pinnedMealTypeId
        }
        return mealType(at: food.date)?.id
    }
}
