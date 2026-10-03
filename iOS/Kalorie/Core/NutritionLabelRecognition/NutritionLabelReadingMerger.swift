//
//  NutritionLabelReadingMerger.swift
//  Kalorie
//
//  Created by Josef Antoni on 03.10.2026.
//

import Foundation

enum NutritionLabelReadingMerger {

    // MARK: - Properties

    private static let minAgreement = 2

    // MARK: - Functions

    static func merge(_ readings: [NutritionLabelReading]) -> NutritionLabelReading {
        NutritionLabelReading(
            energyKJ: agreed(readings, \.energyKJ),
            caloriesPerHundredGrams: agreed(readings, \.caloriesPerHundredGrams),
            fat: agreed(readings, \.fat),
            fatSaturated: agreed(readings, \.fatSaturated),
            fatUnsaturatedFattyAcids: agreed(readings, \.fatUnsaturatedFattyAcids),
            carbohydrate: agreed(readings, \.carbohydrate),
            carbohydratePureSugar: agreed(readings, \.carbohydratePureSugar),
            fiber: agreed(readings, \.fiber),
            protein: agreed(readings, \.protein),
            salt: agreed(readings, \.salt),
            measure: agreed(readings, \.measure)
        )
    }

    // OCR errors differ from frame to frame, so a value read identically in several frames is far more
    // likely correct than one that appeared once.
    private static func agreed<T: Hashable>(_ readings: [NutritionLabelReading], _ field: KeyPath<NutritionLabelReading, T?>) -> T? {
        let values = readings.compactMap { $0[keyPath: field] }
        var counts: [T: Int] = [:]
        for value in values { counts[value, default: 0] += 1 }
        guard let best = counts.values.max(), best >= minAgreement else { return nil }
        return values.first { counts[$0] == best }
    }
}
