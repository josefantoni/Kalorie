//
//  FoodItem.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI


struct FoodItemView: View {
    
    // MARK: - Properties
    
    var foodItem: FoodItem

    
    // MARK: - Init

    init(_ foodItem: FoodItem) {
        self.foodItem = foodItem
    }
    

    // MARK: - Body

    var body: some View {
        VStack {
            HStack {
                Text(foodItem.timeFormatted).padding(.leading)
                Text(foodItem.name ?? "")
                Spacer()
                Text(foodItem.caloriesFormatted).padding(.trailing)
            }
        }
        .contentShape(Rectangle())
    }
}
