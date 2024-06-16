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

    var mealType: MealType

    
    // MARK: - Init

    init(_ mealType: MealType) {
        self.mealType = mealType
    }
    
    
    // MARK: - Body

    var body: some View {
        HStack {
            Text(mealType.name ?? "?")
            Spacer()
            Text(formatTime)
        }
    }
    
    var formatTime: String {
        let startTime = mealType.startTime.toDate.formatDateStyle(with: "HH:mm")
        let endTime = mealType.endTime.toDate.formatDateStyle(with: "HH:mm")
        return "\(startTime) - \(endTime)"
    }
}


// MARK: - Preview

#Preview {
    
    let container = PersistentContainer.container
    guard let startTime = Calendar.current.date(bySettingHour: 5, minute: 0, second: 0, of: Date.now) else {
        fatalError("No toto? Nějak se pokazilo vytváření dne v: 'firstTimeAppLaunchSetup'")
    }
    let mealType = MealType(
        id: 0,
        name: "Snídaně",
        startTime: startTime,
        endTime: startTime.withAddedHours(hours: 3),
        context: container.viewContext
    )
    PersistentContainer.save(container: container)
    return MealTypeItemView(mealType)
}
