//
//  DashboardRouter.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct DashboardRouter {

    // MARK: - Functions

    func makeMealTypeSheetView(mealTypes: [MealTypeDomain]) -> MealTypeSheetView {
        MealTypeSheetConfigurator().createView(mealTypes: mealTypes)
    }

    func makeAddFoodSheetView(withBarcodeScan: Bool = false) -> AddFoodSheetView {
        AddFoodSheetConfigurator().createView(withBarcodeScan: withBarcodeScan)
    }
}
