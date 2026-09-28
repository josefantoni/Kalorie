//
//  SettingsConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct SettingsConfigurator {

    // MARK: - Properties

    private let maintainerClaimCache = MaintainerClaimCache()

    // MARK: - Functions

    func createView(mealTypes: [MealTypeDomain], onMealTypesChanged: @escaping () -> Void = {}) -> SettingsView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return SettingsView(
            router: SettingsRouter(
                moderationConfigurator: ModerationConfigurator(dataProvider: dataProvider, authProvider: authProvider)
            ),
            viewModel: SettingsViewModel(
                mealTypes: mealTypes,
                onMealTypesChanged: onMealTypesChanged,
                createMealType: CreateMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMealType: DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMealTypeTimes: UpdateMealTypeTimesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchMaintainerClaim: FetchMaintainerClaimUseCase(cache: maintainerClaimCache)
            )
        )
    }
}
