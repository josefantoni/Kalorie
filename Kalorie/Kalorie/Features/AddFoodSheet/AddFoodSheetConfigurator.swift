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
                fetchFavouriteFoods: FetchFavouriteFoodsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                onFoodSaved: onFoodSaved,
                isScannerVisible: withBarcodeScan
            )
        ) { [self] item, isFavourite, onSaved, onFavouriteChanged in
            FoodQuantityView(
                viewModel: FoodQuantityViewModel(
                    item: item,
                    saveFoodConsumed: SaveFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider),
                    selectedDate: date,
                    isFavourite: isFavourite,
                    addFavouriteFood: AddFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                    removeFavouriteFood: RemoveFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider),
                    onSaved: onSaved,
                    onFavouriteChanged: onFavouriteChanged
                )
            )
        }
    }
}
