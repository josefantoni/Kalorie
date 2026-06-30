//
//  FoodConsumed.swift
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
            Text(foodConsumed.date.formatDateStyle(with: "HH:mm"))
            Text(foodConsumed.name)
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
            name: "Jogurt bílý",
            weight: 200,
            date: .now,
            calories: 140,
            mealTypeId: nil
        )
    )
}
