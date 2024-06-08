//
//  FoodItem.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI

struct FoodItemView: View {
    var foodName: String

    init(_ foodName: String) {
        self.foodName = foodName
    }
    
    var body: some View {
        VStack {
            HStack {
                Text("11:00")
                Spacer()
                Text(foodName)
                Spacer()
                Text("1000 kcal")
            }
        }
    }
}

#Preview {
    FoodItemView("Snídaně")
}
