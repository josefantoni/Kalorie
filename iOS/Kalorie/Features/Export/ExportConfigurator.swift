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
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return ExportView(
            viewModel: ExportViewModel(
                mealTypes: mealTypes,
                generateFoodExport: GenerateFoodExportUseCase(
                    fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCase(
                        dataProvider: dataProvider,
                        authProvider: authProvider
                    ),
                    fetchBodyWeights: FetchBodyWeightsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                    fetchAppSettings: FetchAppSettingsUseCase(dataProvider: dataProvider, authProvider: authProvider)
                )
            )
        )
    }
}
