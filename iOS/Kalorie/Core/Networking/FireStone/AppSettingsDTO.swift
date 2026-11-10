//
//  AppSettingsDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

struct AppSettingsDTO: Codable {

    // MARK: - Properties

    let showBodyWeight: Bool?
    let bodyWeightUnit: String?
    let showDailyGoal: Bool?
    let showGoalPercent: Bool?
    let showGoalBars: Bool?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case showBodyWeight = "show_body_weight"
        case bodyWeightUnit = "body_weight_unit"
        case showDailyGoal = "show_daily_goal"
        case showGoalPercent = "show_goal_percent"
        case showGoalBars = "show_goal_bars"
    }

    // MARK: - Init

    init(settings: AppSettingsDomain) {
        showBodyWeight = settings.showBodyWeight
        bodyWeightUnit = settings.bodyWeightUnit.rawValue
        showDailyGoal = settings.showDailyGoal
        showGoalPercent = settings.showGoalPercent
        showGoalBars = settings.showGoalBars
    }

    // MARK: - Functions

    func asDomain() -> AppSettingsDomain {
        AppSettingsDomain(
            showBodyWeight: showBodyWeight ?? AppSettingsDomain.defaults.showBodyWeight,
            bodyWeightUnit: bodyWeightUnit.flatMap(BodyWeightUnit.init(rawValue:)) ?? AppSettingsDomain.defaults.bodyWeightUnit,
            showDailyGoal: showDailyGoal ?? AppSettingsDomain.defaults.showDailyGoal,
            showGoalPercent: showGoalPercent ?? AppSettingsDomain.defaults.showGoalPercent,
            showGoalBars: showGoalBars ?? AppSettingsDomain.defaults.showGoalBars
        )
    }
}
