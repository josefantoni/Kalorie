//
//  ExportConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

struct ExportConfigurator {

    // MARK: - Functions

    func createView(mealTypes: [MealTypeDomain]) -> ExportView {
        ExportView(
            viewModel: ExportViewModel(
                mealTypes: mealTypes,
                generateFoodExport: GenerateFoodExportUseCase(
                    fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCase(
                        dataProvider: FirestoreDataProvider(),
                        authProvider: AuthProvider()
                    )
                )
            )
        )
    }
}
