//
//  DashboardConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct DashboardConfigurator {

    // MARK: - Functions

    func createView() -> DashboardView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return DashboardView(
            viewModel: DashboardViewModel(
                fetchMealTypes: FetchMealTypesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCase(dataProvider: dataProvider, authProvider: authProvider),
                setupDefaultMeals: SetupDefaultMealsUseCase(dataProvider: dataProvider, authProvider: authProvider)
            ),
            router: DashboardRouter(
                mealTypeSheetConfigurator: MealTypeSheetConfigurator(),
                addFoodSheetConfigurator: AddFoodSheetConfigurator(dataProvider: dataProvider, authProvider: authProvider),
                foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator(dataProvider: dataProvider, authProvider: authProvider)
            )
        )
    }
}
