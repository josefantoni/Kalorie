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
        HStack {
            Text(foodConsumed.weight.formattedGrams(fractionDigits: 0))
            Text(foodConsumed.displayName)
            Spacer()
            VStack {
                Text("\(foodConsumed.calories) kcal")
            }
        }
        .padding(.all)
        .frame(height: 80)
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
            protein: 8,
            carbohydrate: 16,
            carbohydrateSugar: 12,
            fat: 3.5,
            fatUnsaturated: 1.2,
            fiber: 0,
            salt: 0.1
        )
    )
}
