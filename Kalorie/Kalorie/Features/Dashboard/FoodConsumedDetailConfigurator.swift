//
//  FoodConsumedDetailConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import Foundation

struct FoodConsumedDetailConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(food: FoodConsumedDomain, mealTypes: [MealTypeDomain], onFoodUpdated: @escaping () -> Void) -> FoodConsumedDetailView {
        FoodConsumedDetailView(
            viewModel: FoodConsumedDetailViewModel(
                food: food,
                mealTypes: mealTypes,
                updateFoodConsumed: UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
                assignFoodMealType: AssignFoodMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchMealTypes: FetchMealTypesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                isFavouriteFood: IsFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                addFavouriteFood: AddFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                removeFavouriteFood: RemoveFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCase(),
                onFoodUpdated: onFoodUpdated
            )
        )
    }
}
