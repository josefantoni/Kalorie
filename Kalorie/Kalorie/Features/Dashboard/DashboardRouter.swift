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
    private let foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator

    // MARK: - Init

    init(
        mealTypeSheetConfigurator: MealTypeSheetConfigurator,
        addFoodSheetConfigurator: AddFoodSheetConfigurator,
        foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator
    ) {
        self.mealTypeSheetConfigurator = mealTypeSheetConfigurator
        self.addFoodSheetConfigurator = addFoodSheetConfigurator
        self.foodConsumedDetailConfigurator = foodConsumedDetailConfigurator
    }

    // MARK: - Functions

    func makeMealTypeSheetView(mealTypes: [MealTypeDomain], onMealTypesChanged: @escaping () -> Void = {}) -> MealTypeSheetView {
        mealTypeSheetConfigurator.createView(mealTypes: mealTypes, onMealTypesChanged: onMealTypesChanged)
    }

    func makeAddFoodSheetView(for date: Date, onFoodSaved: @escaping () -> Void = {}, withBarcodeScan: Bool = false) -> AddFoodSheetView {
        addFoodSheetConfigurator.createView(date: date, onFoodSaved: onFoodSaved, withBarcodeScan: withBarcodeScan)
    }

    func makeFoodConsumedDetailView(food: FoodConsumedDomain, onFoodUpdated: @escaping () -> Void = {}) -> FoodConsumedDetailView {
        foodConsumedDetailConfigurator.createView(food: food, onFoodUpdated: onFoodUpdated)
    }
}
