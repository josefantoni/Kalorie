//
//  FoodConsumedView.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI

struct FoodConsumedView: View {

    // MARK: - Properties

    var foodConsumed: FoodConsumedDomain

    // MARK: - Init

    init(_ foodConsumed: FoodConsumedDomain) {
        self.foodConsumed = foodConsumed
    }

    // MARK: - Body

    var body: some View {
        HStack(alignment: .center, spacing: 4) {
            VStack(alignment: .leading, spacing: 2) {
                Text(foodConsumed.displayName)
                    .font(.callout)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .truncationMode(.tail)
                Text(foodConsumed.weight.formattedAmount(measure: foodConsumed.measure, fractionDigits: 0))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text("\(foodConsumed.calories) kcal")
                .font(.callout)
                .monospacedDigit()
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.leading)
        .padding(.trailing, 4)
        .frame(minHeight: 50)
    }
}

// MARK: - Preview

#Preview {
    FoodConsumedView(
        FoodConsumedDomain(
            id: "1",
            foodItemId: "1",
            foodItemKind: .catalogue,
            czName: "Jogurt bílý",
            engName: "White yoghurt",
            weight: 200,
            date: .now,
            calories: 140,
            caloriesPerHundredGrams: 70,
            energyKJ: 586,
            protein: 8,
            carbohydrate: 16,
            carbohydrateSugar: 12,
            fat: 3.5,
            fatSaturated: 2.2,
            fatUnsaturated: 1.2,
            fiber: 0,
            salt: 0.1,
            mealTypeId: nil
        )
    )
}
