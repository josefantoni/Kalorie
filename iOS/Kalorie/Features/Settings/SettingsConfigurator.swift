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

    func createView(
        mealTypes: [MealTypeDomain],
        appSettings: AppSettingsDomain = .defaults,
        hasLoadedAppSettings: Bool = true,
        onMealTypesChanged: @escaping () -> Void = {},
        onAppSettingsChanged: @escaping (AppSettingsDomain) -> Void = { _ in }
    ) -> SettingsView {
        let dataProvider = FirestoreDataProvider()
        let authProvider = AuthProvider()
        return SettingsView(
            router: SettingsRouter(
                moderationConfigurator: ModerationConfigurator(dataProvider: dataProvider, authProvider: authProvider)
            ),
            viewModel: SettingsViewModel(
                mealTypes: mealTypes,
                appSettings: appSettings,
                onMealTypesChanged: onMealTypesChanged,
                onAppSettingsChanged: onAppSettingsChanged,
                saveAppSettings: SaveAppSettingsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                createMealType: CreateMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                deleteMealType: DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: authProvider),
                updateMealTypeTimes: UpdateMealTypeTimesUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchMaintainerClaim: FetchMaintainerClaimUseCase(cache: maintainerClaimCache),
                hasLoadedAppSettings: hasLoadedAppSettings
            )
        )
    }
}
