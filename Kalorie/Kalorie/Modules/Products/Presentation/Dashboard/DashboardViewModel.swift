//
//  DashboardViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import Combine
import CoreData


// MARK: - State

struct DashboardViewState {
    
    var foodItems: [FoodItem] = []
    var mealTypes: [MealType] = []
    var container: NSPersistentContainer
}


final class DashboardViewModel: ObservableObject {
    
    // MARK: - Properties
    
    let isDemo: Bool
    
    
    // MARK: - Init
    
    init(isDemo: Bool) {
        self.isDemo = isDemo
    }
}


// MARK: - Helpers

extension DashboardViewModel {
    
    static var demo: DashboardViewModel {
        DashboardViewModel(
            isDemo: true
        )
    }
    
    static var live: DashboardViewModel {
        DashboardViewModel(
            isDemo: false
        )
    }
}
