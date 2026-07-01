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
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return MealTypeSheetView(
            viewModel: MealTypeSheetViewModel(
                mealTypes: mealTypes,
                createMealType: CreateMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMealType: DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMealTypeTimes: UpdateMealTypeTimesUseCase(dataProvider: dataProvider, authProvider: authProvider)
            )
        )
    }
}
