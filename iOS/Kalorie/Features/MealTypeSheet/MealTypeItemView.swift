//
//  MealTypeItemView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI

struct MealTypeItemView: View {

    // MARK: - Properties

    var mealType: MealTypeDomain

    // MARK: - Init

    init(_ mealType: MealTypeDomain) {
        self.mealType = mealType
    }

    // MARK: - Body

    var body: some View {
        HStack {
            Text(mealType.name)
            Spacer()
            Text(formatTime)
        }
    }

    // MARK: - Functions

    var formatTime: String {
        let startTime = MealTypeDomain.clockTime(minutes: mealType.startMinutes)
        let endTime = MealTypeDomain.clockTime(minutes: mealType.endMinutes)
        return "\(startTime) - \(endTime)"
    }
}

// MARK: - Preview

#Preview {
    MealTypeItemView(
        MealTypeDomain(
            id: "1",
            name: "Snídaně",
            startMinutes: 7 * 60,
            endMinutes: 9 * 60
        )
    )
}
