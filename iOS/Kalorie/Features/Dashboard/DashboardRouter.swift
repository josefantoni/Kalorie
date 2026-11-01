//
//  DashboardRouter.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct DashboardRouter {

    // MARK: - Properties

    private let settingsConfigurator: SettingsConfigurator
    private let addFoodSheetConfigurator: AddFoodSheetConfigurator
    private let foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator
    private let accountConfigurator: AccountConfigurator
    private let bodyWeightHistoryConfigurator: BodyWeightHistoryConfigurator

    // MARK: - Init

    init(
        settingsConfigurator: SettingsConfigurator,
        addFoodSheetConfigurator: AddFoodSheetConfigurator,
        foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator,
        accountConfigurator: AccountConfigurator,
        bodyWeightHistoryConfigurator: BodyWeightHistoryConfigurator
    ) {
        self.settingsConfigurator = settingsConfigurator
        self.addFoodSheetConfigurator = addFoodSheetConfigurator
        self.foodConsumedDetailConfigurator = foodConsumedDetailConfigurator
        self.accountConfigurator = accountConfigurator
        self.bodyWeightHistoryConfigurator = bodyWeightHistoryConfigurator
    }

    // MARK: - Functions

    func makeSettingsView(
        mealTypes: [MealTypeDomain],
        appSettings: AppSettingsDomain,
        hasLoadedAppSettings: Bool,
        onMealTypesChanged: @escaping () -> Void = {},
        onAppSettingsChanged: @escaping (AppSettingsDomain) -> Void = { _ in }
    ) -> SettingsView {
        settingsConfigurator.createView(
            mealTypes: mealTypes,
            appSettings: appSettings,
            hasLoadedAppSettings: hasLoadedAppSettings,
            onMealTypesChanged: onMealTypesChanged,
            onAppSettingsChanged: onAppSettingsChanged
        )
    }

    func makeAddFoodSheetView(
        for date: Date,
        mealTypes: [MealTypeDomain],
        onFoodSaved: @escaping () -> Void = {},
        withBarcodeScan: Bool = false
    ) -> AddFoodSheetView {
        addFoodSheetConfigurator.createView(
            date: date,
            mealTypes: mealTypes,
            onFoodSaved: onFoodSaved,
            withBarcodeScan: withBarcodeScan
        )
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

    func makeBodyWeightHistoryView(unit: BodyWeightUnit, selectedDay: Date, onChanged: @escaping () -> Void) -> BodyWeightHistoryView {
        bodyWeightHistoryConfigurator.createView(unit: unit, selectedDay: selectedDay, onChanged: onChanged)
    }
}
