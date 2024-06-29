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
    
    var foodConsumed: FoodConsumed

    
    // MARK: - Init

    init(_ foodConsumed: FoodConsumed) {
        self.foodConsumed = foodConsumed
    }
    

    // MARK: - Body

    var body: some View {
        VStack {
            HStack {
                Text(foodConsumed.timeFormatted)
                Text(foodConsumed.name ?? "")
                Spacer()
                VStack {
                    Text(foodConsumed.caloriesFormatted)
//                    Text(foodConsumed.)
                }
            }
            .padding([.all])
            .frame(height: 80)
        }
        .background(.green)
    }
}

#Preview {
    let container = PersistentContainer.container
    let foodConsumed = DemoData.demofoodConsumed(on: container.viewContext)
    PersistentContainer.save(container: container)
    return FoodConsumedView(foodConsumed)
}
