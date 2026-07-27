//
//  AddFoodSheetConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct AddFoodSheetConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(date: Date, onFoodSaved: @escaping () -> Void = {}, withBarcodeScan: Bool = false) -> AddFoodSheetView {
        AddFoodSheetView(
            viewModel: AddFoodSheetViewModel(
                searchFoodItems: SearchFoodItemsUseCase(dataProvider: dataProvider),
                createFoodItem: CreateFoodItemUseCase(dataProvider: dataProvider),
                searchFoodExternally: SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCase(),
                onFoodSaved: onFoodSaved,
                isScannerVisible: withBarcodeScan
            ),
            makeFoodQuantityView: { [self] item, onSaved in
                FoodQuantityView(
                    viewModel: FoodQuantityViewModel(
                        item: item,
                        saveFoodConsumed: SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        selectedDate: date,
                        onSaved: onSaved
                    )
                )
            }
        )
    }
}
