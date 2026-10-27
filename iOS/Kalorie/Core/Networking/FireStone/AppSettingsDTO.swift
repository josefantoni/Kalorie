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

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case showBodyWeight = "show_body_weight"
        case bodyWeightUnit = "body_weight_unit"
    }

    // MARK: - Init

    init(settings: AppSettingsDomain) {
        showBodyWeight = settings.showBodyWeight
        bodyWeightUnit = settings.bodyWeightUnit.rawValue
    }

    // MARK: - Functions

    func asDomain() -> AppSettingsDomain {
        AppSettingsDomain(
            showBodyWeight: showBodyWeight ?? AppSettingsDomain.defaults.showBodyWeight,
            bodyWeightUnit: bodyWeightUnit.flatMap(BodyWeightUnit.init(rawValue:)) ?? AppSettingsDomain.defaults.bodyWeightUnit
        )
    }
}
