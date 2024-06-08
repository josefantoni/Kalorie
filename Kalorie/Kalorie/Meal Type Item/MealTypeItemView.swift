//
//  MealTypeItemView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI

struct MealTypeItemView: View {
    var mealName: String

    init(_ foodName: String) {
        self.mealName = foodName
    }
    
    var body: some View {
        HStack {
            Text(mealName)
        }
    }
}

#Preview {
    MealTypeItemView("Snídaně")
}
