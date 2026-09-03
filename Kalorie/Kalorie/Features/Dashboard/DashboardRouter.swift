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
    private let accountConfigurator: AccountConfigurator
    private let myCreatedMealEditorConfigurator: MyCreatedMealEditorConfigurator

    // MARK: - Init

    init(
        mealTypeSheetConfigurator: MealTypeSheetConfigurator,
        addFoodSheetConfigurator: AddFoodSheetConfigurator,
        foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator,
        accountConfigurator: AccountConfigurator,
        myCreatedMealEditorConfigurator: MyCreatedMealEditorConfigurator
    ) {
        self.mealTypeSheetConfigurator = mealTypeSheetConfigurator
        self.addFoodSheetConfigurator = addFoodSheetConfigurator
        self.foodConsumedDetailConfigurator = foodConsumedDetailConfigurator
        self.accountConfigurator = accountConfigurator
        self.myCreatedMealEditorConfigurator = myCreatedMealEditorConfigurator
    }

    // MARK: - Functions

    func makeMealTypeSheetView(mealTypes: [MealTypeDomain], onMealTypesChanged: @escaping () -> Void = {}) -> MealTypeSheetView {
        mealTypeSheetConfigurator.createView(mealTypes: mealTypes, onMealTypesChanged: onMealTypesChanged)
    }

    func makeAddFoodSheetView(
        for date: Date,
        mealTypes: [MealTypeDomain],
        onFoodSaved: @escaping () -> Void = {},
        onCreateMealRequested: @escaping () -> Void = {},
        withBarcodeScan: Bool = false
    ) -> AddFoodSheetView {
        addFoodSheetConfigurator.createView(
            date: date,
            mealTypes: mealTypes,
            onFoodSaved: onFoodSaved,
            onCreateMealRequested: onCreateMealRequested,
            withBarcodeScan: withBarcodeScan
        )
    }

    func makeMyCreatedMealEditorView(existingMeal: MyCreatedMealDomain? = nil, onSaved: @escaping () -> Void = {}) -> MyCreatedMealEditorView {
        myCreatedMealEditorConfigurator.createView(existingMeal: existingMeal, onSaved: onSaved)
    }

    func makeFoodConsumedDetailView(
        food: FoodConsumedDomain,
        mealTypes: [MealTypeDomain],
        onFoodUpdated: @escaping () -> Void = {}
    ) -> FoodConsumedDetailView {
        foodConsumedDetailConfigurator.createView(food: food, mealTypes: mealTypes, onFoodUpdated: onFoodUpdated)
    }

    func makeAccountView() -> AccountView {
        accountConfigurator.createView()
    }
}
