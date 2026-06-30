//
//  MealTypeSheetConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct MealTypeSheetConfigurator {

    // MARK: - Functions

    func createView(mealTypes: [MealTypeDomain]) -> MealTypeSheetView {
        let context = PersistentContainer.shared.viewContext
        return MealTypeSheetView(
            viewModel: MealTypeSheetViewModel(
                mealTypes: mealTypes,
                createMealType: CreateMealTypeUseCase(context: context),
                deleteMealType: DeleteMealTypeUseCase(context: context)
            )
        )
    }
}
