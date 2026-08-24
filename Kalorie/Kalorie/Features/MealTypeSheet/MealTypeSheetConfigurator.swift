//
//  MealTypeSheetConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct MealTypeSheetConfigurator {

    // MARK: - Functions

    func createView(mealTypes: [MealTypeDomain], onMealTypesChanged: @escaping () -> Void = {}) -> MealTypeSheetView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        let editorConfigurator = MyCreatedMealEditorConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        return MealTypeSheetView(
            viewModel: MealTypeSheetViewModel(
                mealTypes: mealTypes,
                onMealTypesChanged: onMealTypesChanged,
                createMealType: CreateMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMealType: DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMealTypeTimes: UpdateMealTypeTimesUseCase(dataProvider: dataProvider, authProvider: authProvider)
            ),
            myCreatedMealListViewModel: MyCreatedMealListViewModel(
                fetchMyCreatedMeals: FetchMyCreatedMealsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMyCreatedMeal: DeleteMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider)
            )
        ) { meal, onSaved in
            editorConfigurator.createView(existingMeal: meal, onSaved: onSaved)
        }
    }
}
