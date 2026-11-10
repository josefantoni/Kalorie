//
//  DailyGoalsConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import Foundation

struct DailyGoalsConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func createView(history: DailyGoalHistory, unit: BodyWeightUnit, onChanged: @escaping (DailyGoalHistory) -> Void) -> DailyGoalsView {
        DailyGoalsView(
            viewModel: DailyGoalsViewModel(
                history: history,
                unit: unit,
                fetchBodyWeights: FetchBodyWeightsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                saveDailyGoal: SaveDailyGoalUseCase(
                    fetchDailyGoals: FetchDailyGoalsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                    dataProvider: dataProvider,
                    authProvider: authProvider
                ),
                saveBodyWeight: SaveBodyWeightUseCase(dataProvider: dataProvider, authProvider: authProvider),
                onChanged: onChanged
            )
        )
    }
}
