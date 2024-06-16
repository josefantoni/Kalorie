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
}
