//
//  MealTypeSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import CoreData


struct MealTypeSheetViewState {
    
    var mealTypes: [MealType]
    var container: NSPersistentContainer
    
    // Hidden UI
    var showingAlert = false
    var isAddButtonHidden = true

    // New meal creation
    var alertTitle = ""
    var newMealName = ""
    var newMealStart = Date.now
    var newMealEnd = Date.now
}


final class MealTypeSheetViewModel: ObservableObject {
    
    // MARK: - Properties

    @Published var state: MealTypeSheetViewState
    
    
    // MARK: - Init
    
    init(_ state: MealTypeSheetViewState) {
        self.state = state
    }
    
    
    // MARK: - Function
    
    func delete(by indexPath: IndexPath.Element) {
        state.container.viewContext.delete(state.mealTypes.remove(at: indexPath))
        PersistentContainer.save(container: state.container)
    }
    
    func getPossibleTimeForNewMealCreation() -> (possibleStart: Date, possibleEnd: Date) {
        // we show default value at night - after dinner
        guard let possibleStart = state.mealTypes.map({ $0.endTime }).max()?.toDate else {
            return (Date.now, Date.now.withAddedMinutes(minutes: 30))
        }
        return (possibleStart, possibleStart.withAddedMinutes(minutes: 30))
    }
}
