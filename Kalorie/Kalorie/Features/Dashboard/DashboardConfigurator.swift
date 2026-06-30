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
        let context = PersistentContainer.shared.viewContext
        return DashboardView(
            viewModel: DashboardViewModel(
                fetchMealTypes: FetchMealTypesUseCase(context: context),
                fetchFoodsConsumed: FetchFoodsConsumedUseCase(context: context),
                setupDefaultMeals: SetupDefaultMealsUseCase(context: context)
            ),
            router: DashboardRouter(
                mealTypeSheetConfigurator: MealTypeSheetConfigurator(),
                addFoodSheetConfigurator: AddFoodSheetConfigurator()
            )
        )
    }
}
