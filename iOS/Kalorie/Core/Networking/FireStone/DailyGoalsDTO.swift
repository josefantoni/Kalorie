//
//  DailyGoalsDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import Foundation

struct DailyGoalsDTO: Codable, Equatable {

    // MARK: - Properties

    let goals: [DailyGoalEntryDTO]?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case goals
    }

    // MARK: - Init

    init(history: DailyGoalHistory) {
        goals = history.entries.map(DailyGoalEntryDTO.init)
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let entries = try? container.decodeIfPresent([LossyEntry].self, forKey: .goals)
        goals = entries?.compactMap(\.entry)
    }

    // MARK: - Functions

    func asDomain() -> DailyGoalHistory {
        DailyGoalHistory(entries: (goals ?? []).compactMap { $0.asDomain() }.sorted { $0.validFrom < $1.validFrom })
    }
}

private struct LossyEntry: Decodable {
    let entry: DailyGoalEntryDTO?

    init(from decoder: Decoder) throws {
        entry = try? DailyGoalEntryDTO(from: decoder)
    }
}

struct DailyGoalEntryDTO: Codable, Equatable {

    // MARK: - Properties

    let validFrom: String?
    let kcalGoal: Double?
    let macroMode: String?
    let carbsPercent: Double?
    let proteinPercent: Double?
    let fatPercent: Double?
    let carbsGrams: Double?
    let proteinGrams: Double?
    let fatGrams: Double?
    let sex: String?
    let birthYear: Int?
    let heightCm: Double?
    let weightKg: Double?
    let activityLevel: String?
    let hasUnreadableDecisiveField: Bool

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case validFrom = "valid_from"
        case kcalGoal = "kcal_goal"
        case macroMode = "macro_mode"
        case carbsPercent = "carbs_pct"
        case proteinPercent = "protein_pct"
        case fatPercent = "fat_pct"
        case carbsGrams = "carbs_g"
        case proteinGrams = "protein_g"
        case fatGrams = "fat_g"
        case sex
        case birthYear = "birth_year"
        case heightCm = "height_cm"
        case weightKg = "weight_kg"
        case activityLevel = "activity_level"
    }

    // MARK: - Init

    init(goal: DailyGoalDomain) {
        validFrom = goal.validFrom
        kcalGoal = goal.kcalGoal
        macroMode = goal.kcalGoal == nil ? nil : goal.macroMode.rawValue
        carbsPercent = goal.macroMode == .percent ? goal.carbsPercent : nil
        proteinPercent = goal.macroMode == .percent ? goal.proteinPercent : nil
        fatPercent = goal.macroMode == .percent ? goal.fatPercent : nil
        carbsGrams = goal.macroMode == .grams ? goal.carbsGrams : nil
        proteinGrams = goal.macroMode == .grams ? goal.proteinGrams : nil
        fatGrams = goal.macroMode == .grams ? goal.fatGrams : nil
        sex = goal.sex?.rawValue
        birthYear = goal.birthYear
        heightCm = goal.heightCm
        weightKg = goal.weightKg
        activityLevel = goal.activityLevel?.rawValue
        hasUnreadableDecisiveField = false
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        validFrom = try? container.decodeIfPresent(String.self, forKey: .validFrom)
        kcalGoal = try? container.decodeIfPresent(Double.self, forKey: .kcalGoal)
        macroMode = try? container.decodeIfPresent(String.self, forKey: .macroMode)
        carbsPercent = try? container.decodeIfPresent(Double.self, forKey: .carbsPercent)
        proteinPercent = try? container.decodeIfPresent(Double.self, forKey: .proteinPercent)
        fatPercent = try? container.decodeIfPresent(Double.self, forKey: .fatPercent)
        carbsGrams = try? container.decodeIfPresent(Double.self, forKey: .carbsGrams)
        proteinGrams = try? container.decodeIfPresent(Double.self, forKey: .proteinGrams)
        fatGrams = try? container.decodeIfPresent(Double.self, forKey: .fatGrams)
        sex = try? container.decodeIfPresent(String.self, forKey: .sex)
        birthYear = try? container.decodeIfPresent(Int.self, forKey: .birthYear)
        heightCm = try? container.decodeIfPresent(Double.self, forKey: .heightCm)
        weightKg = try? container.decodeIfPresent(Double.self, forKey: .weightKg)
        activityLevel = try? container.decodeIfPresent(String.self, forKey: .activityLevel)
        hasUnreadableDecisiveField = (kcalGoal == nil && Self.isPresentAndNotNull(container, .kcalGoal))
            || (macroMode == nil && Self.isPresentAndNotNull(container, .macroMode))
    }

    // MARK: - Functions

    func asDomain() -> DailyGoalDomain? {
        guard
            let validFrom,
            !hasUnreadableDecisiveField
        else { return nil }
        var mode = GoalMacroMode.percent
        if let macroMode {
            guard let known = GoalMacroMode(rawValue: macroMode) else { return nil }
            mode = known
        }
        return DailyGoalDomain(
            validFrom: validFrom,
            kcalGoal: kcalGoal,
            macroMode: mode,
            carbsPercent: carbsPercent,
            proteinPercent: proteinPercent,
            fatPercent: fatPercent,
            carbsGrams: carbsGrams,
            proteinGrams: proteinGrams,
            fatGrams: fatGrams,
            sex: sex.flatMap(GoalSex.init(rawValue:)),
            birthYear: birthYear,
            heightCm: heightCm,
            weightKg: weightKg,
            activityLevel: activityLevel.flatMap(GoalActivityLevel.init(rawValue:))
        )
    }

    private static func isPresentAndNotNull(_ container: KeyedDecodingContainer<CodingKeys>, _ key: CodingKeys) -> Bool {
        container.contains(key) && ((try? container.decodeNil(forKey: key)) != true)
    }
}
