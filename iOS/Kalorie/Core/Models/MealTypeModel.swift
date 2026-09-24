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
        guard let id = MealWindowsKt.mealWindowAt(minutes: date.minutesSinceMidnight, windows: mealWindows)?.id else {
            return nil
        }
        return first { $0.id == id }
    }

    func resolvedMealTypeId(for food: FoodConsumedDomain) -> String? {
        MealWindowsKt.resolvedMealWindowId(
            minutes: food.date.minutesSinceMidnight,
            pinnedId: food.mealTypeId,
            windows: mealWindows
        )
    }

    private var mealWindows: [MealWindow] {
        map {
            MealWindow(
                id: $0.id,
                startMinutes: $0.startTime.minutesSinceMidnight,
                endMinutes: $0.endTime.minutesSinceMidnight
            )
        }
    }
}
