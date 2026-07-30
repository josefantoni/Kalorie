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

    func createView(food: FoodConsumedDomain, onFoodUpdated: @escaping () -> Void) -> FoodConsumedDetailView {
        FoodConsumedDetailView(
            viewModel: FoodConsumedDetailViewModel(
                food: food,
                updateFoodConsumed: UpdateFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
                onFoodUpdated: onFoodUpdated
            )
        )
    }
}
