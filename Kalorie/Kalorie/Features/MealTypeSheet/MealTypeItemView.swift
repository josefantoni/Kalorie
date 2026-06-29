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
        let startTime = mealType.startTime.formatDateStyle(with: "HH:mm")
        let endTime = mealType.endTime.formatDateStyle(with: "HH:mm")
        return "\(startTime) - \(endTime)"
    }
}

// MARK: - Preview

#Preview {
    MealTypeItemView(
        MealTypeDomain(
            id: 1,
            name: "Snídaně",
            startTime: Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: .now) ?? .now
        )
    )
}
