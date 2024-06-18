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
    var showMealTypeSheet = false
}


final class DashboardViewModel: ObservableObject {
    
    // MARK: - Properties
    
    @Published var state: DashboardViewState
    
    
    // MARK: - Init
    
    init(container: NSPersistentContainer) {
        self.state = .init(container: container)
        self.firstTimeAppLaunchSetupIfNeeded()
    }

    
    // MARK: - Function

    func firstTimeAppLaunchSetupIfNeeded() {
        if UserDefaults.standard.object(forKey: "FirstOpen") == nil {
            firstTimeAppLaunchSetup()
            UserDefaults.standard.set(true, forKey: "FirstOpen")
        } else {
            self.state.mealTypes = getAllMealTypes()
        }
    }
            
    /**
     *   Add breakfast, lunch, dinner as basic meal types
     */
    func firstTimeAppLaunchSetup() {
        guard var startTime = Calendar.current.date(bySettingHour: 5, minute: 0, second: 0, of: Date.now) else {
            fatalError("No toto? Nějak se pokazilo vytváření dne v: 'firstTimeAppLaunchSetup'")
        }
        var endTime = startTime.withAddedHours(hours: 3)
        var id = 0
        let mealNames = ["Snídaně", "Druhá snídaně", "Oběd", "Svačina", "Večeře"]
        
        for mealName in mealNames {
            let meal = MealType(
                id: id,
                name: mealName,
                startTime: startTime,
                endTime: endTime,
                context: state.container.viewContext
            )
            PersistentContainer.save(container: state.container)

            startTime = endTime
            endTime = startTime.withAddedHours(hours: 3)
            id += 1
            state.mealTypes.append(meal)
        }
    }
    
    func getTime(from date: Date) -> (hour: String, minute: String) {
        let hour = Calendar.current.component(.hour, from: date).makeDoubleDigit
        let minute = Calendar.current.component(.minute, from: date).makeDoubleDigit
        return (hour, minute)
    }
    
    func getAllMealTypes() -> [MealType] {
        let request = NSFetchRequest<MealType>(entityName: "MealType")
        var result: [MealType] = []
        do {
            result = try state.container.viewContext.fetch(request)
        } catch let error {
            fatalError("No toto? Nějak se pokazilo 'getAllMealTypes': \(error)")
        }
        return result
    }
}
