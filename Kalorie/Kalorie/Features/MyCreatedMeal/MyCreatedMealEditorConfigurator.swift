//
//  MyCreatedMealEditorConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

struct MyCreatedMealEditorConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(existingMeal: MyCreatedMealDomain? = nil, onSaved: @escaping () -> Void = {}) -> MyCreatedMealEditorView {
        MyCreatedMealEditorView(
            viewModel: MyCreatedMealEditorViewModel(
                searchFoodItems: SearchFoodItemsUseCase(dataProvider: dataProvider),
                searchFoodExternally: SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCase(),
                createMyCreatedMeal: CreateMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMyCreatedMeal: UpdateMyCreatedMealUseCase(dataProvider: dataProvider, authProvider: authProvider),
                existingMeal: existingMeal,
                onSaved: onSaved
            )
        )
    }
}
