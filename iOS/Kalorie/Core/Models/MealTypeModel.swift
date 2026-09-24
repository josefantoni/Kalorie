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
    let startMinutes: Int
    let endMinutes: Int

    // MARK: - Functions

    static func clockTime(minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }
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
                startMinutes: Int32($0.startMinutes),
                endMinutes: Int32($0.endMinutes)
            )
        }
    }
}
