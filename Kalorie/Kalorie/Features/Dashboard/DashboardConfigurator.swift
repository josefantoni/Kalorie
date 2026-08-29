//
//  DashboardConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct DashboardConfigurator {

    // MARK: - Functions

    func createView(mergeStatusReporting: any MergeStatusReporting) -> DashboardView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return DashboardView(
            viewModel: DashboardViewModel(
                fetchMealTypes: FetchMealTypesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCase(dataProvider: dataProvider, authProvider: authProvider),
                setupDefaultMeals: SetupDefaultMealsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteFoodConsumed: DeleteFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
            ),
            router: DashboardRouter(
                mealTypeSheetConfigurator: MealTypeSheetConfigurator(),
                addFoodSheetConfigurator: AddFoodSheetConfigurator(dataProvider: dataProvider, authProvider: authProvider),
                foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator(dataProvider: dataProvider, authProvider: authProvider),
                accountConfigurator: AccountConfigurator(dataProvider: dataProvider, authProvider: authProvider, mergeStatusReporting: mergeStatusReporting),
                myCreatedMealEditorConfigurator: MyCreatedMealEditorConfigurator(dataProvider: dataProvider, authProvider: authProvider)
            )
        )
    }
}
