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
    
    @Published var state: DashboardViewState
    let isDemo: Bool
    
    
    // MARK: - Init
    
    init(isDemo: Bool, container: NSPersistentContainer) {
        self.isDemo = isDemo
        self.state = .init(container: container)
        self.firstTimeAppLaunchSetupIfNeeded()
    }

    
    // MARK: - Function

    func firstTimeAppLaunchSetupIfNeeded() {
        if UserDefaults.standard.object(forKey: "FirstOpen") == nil || isDemo {
            firstTimeAppLaunchSetup()
            UserDefaults.standard.set(true, forKey: "FirstOpen")
        } else {
            self.state.mealTypes = getAllMealTypes()
        }
    }
        
    func createMealTypeIfPossible(
        mealName: String,
        startTime: Date,
        endTime: Date
    ) throws -> MealType {
        if mealName.isEmpty {
            throw CreateMealTypeResult.emptyName
        }
        if state.mealTypes.contains(where: { $0.name == mealName }) {
            throw CreateMealTypeResult.invalidName
        }
        if state.mealTypes.contains(where: {
            startTime.isBetween($0.startTime.toDate, $0.endTime.toDate) ||
            endTime.isBetween($0.startTime.toDate, $0.endTime.toDate)
        }) {
            throw CreateMealTypeResult.invalidTime
        }
        
        let newId = state.mealTypes.map { $0.id }.max() ?? -1 // if nil - there are no mealtypes
        
        let mealType = createMealType(
            id: (newId + 1).toInt,
            mealType: mealName,
            startTime: startTime,
            endTime: endTime
        )
        return mealType
    }
            
    func createMealType(
        id: Int,
        mealType: String,
        startTime: Date,
        endTime: Date
    ) -> MealType {
        let mealType = MealType(
            id: id,
            name: mealType,
            startTime: startTime,
            endTime: endTime,
            context: state.container.viewContext
        )
        PersistentContainer.save(container: state.container)
        return mealType
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
            let meal = createMealType(
                id: id,
                mealType: mealName,
                startTime: startTime,
                endTime: endTime
            )
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
        var storedSportActivities: [MealType] = []
        do {
            storedSportActivities = try state.container.viewContext.fetch(request)
        } catch let error {
            fatalError("No toto? Nějak se pokazilo 'getAllMealTypes': \(error)")
        }
        return storedSportActivities
    }
}


// MARK: - Helpers

extension DashboardViewModel {
    
    static var demo: DashboardViewModel {
        DashboardViewModel(
            isDemo: true,
            container: PersistentContainer.container
        )
    }
    
    static var live: DashboardViewModel {
        DashboardViewModel(
            isDemo: false,
            container: PersistentContainer.container
        )
    }
}
