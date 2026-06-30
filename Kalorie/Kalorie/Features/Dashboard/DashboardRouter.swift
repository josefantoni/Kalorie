//
//  DashboardRouter.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct DashboardRouter {

    // MARK: - Properties

    private let mealTypeSheetConfigurator: MealTypeSheetConfigurator
    private let addFoodSheetConfigurator: AddFoodSheetConfigurator

    // MARK: - Init

    init(
        mealTypeSheetConfigurator: MealTypeSheetConfigurator,
        addFoodSheetConfigurator: AddFoodSheetConfigurator
    ) {
        self.mealTypeSheetConfigurator = mealTypeSheetConfigurator
        self.addFoodSheetConfigurator = addFoodSheetConfigurator
    }

    // MARK: - Functions

    func makeMealTypeSheetView(mealTypes: [MealTypeDomain]) -> MealTypeSheetView {
        mealTypeSheetConfigurator.createView(mealTypes: mealTypes)
    }

    func makeAddFoodSheetView(withBarcodeScan: Bool = false) -> AddFoodSheetView {
        addFoodSheetConfigurator.createView(withBarcodeScan: withBarcodeScan)
    }
}
