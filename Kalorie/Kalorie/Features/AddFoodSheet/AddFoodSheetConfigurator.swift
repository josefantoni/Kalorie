//
//  AddFoodSheetConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct AddFoodSheetConfigurator {

    // MARK: - Functions

    func createView(withBarcodeScan: Bool = false) -> AddFoodSheetView {
        let dataProvider = FirestoreDataProvider()
        return AddFoodSheetView(
            viewModel: AddFoodSheetViewModel(
                fetchFoodItems: FetchFoodItemsUseCase(dataProvider: dataProvider),
                createFoodItem: CreateFoodItemUseCase(dataProvider: dataProvider),
                isScannerVisible: withBarcodeScan
            )
        )
    }
}
