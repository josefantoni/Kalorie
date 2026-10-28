//
//  BodyWeightHistoryConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

struct BodyWeightHistoryConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(unit: BodyWeightUnit, onChanged: @escaping () -> Void) -> BodyWeightHistoryView {
        BodyWeightHistoryView(
            viewModel: BodyWeightHistoryViewModel(
                unit: unit,
                fetchBodyWeights: FetchBodyWeightsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteBodyWeight: DeleteBodyWeightUseCase(dataProvider: dataProvider, authProvider: authProvider),
                onChanged: onChanged
            )
        )
    }
}
