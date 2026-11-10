//
//  DailyGoalModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import Foundation
import MacroKit

enum GoalSex: String, CaseIterable {
    case male
    case female
}

enum GoalMacroMode: String, CaseIterable {
    case percent
    case grams
}

enum GoalActivityLevel: String, CaseIterable {
    case sedentary
    case light
    case moderate
    case high
    case veryHigh = "very_high"

    // MARK: - Properties

    var factor: Double {
        switch self {
        case .sedentary: ActivityLevel.sedentary.factor
        case .light: ActivityLevel.light.factor
        case .moderate: ActivityLevel.moderate.factor
        case .high: ActivityLevel.high.factor
        case .veryHigh: ActivityLevel.veryHigh.factor
        }
    }
}

struct GoalMacroGrams: Equatable {

    // MARK: - Properties

    let protein: Double
    let carbs: Double
    let fat: Double

    var kcal: Double {
        EnergyKt.kcalFromGrams(protein: protein, carbohydrate: carbs, fat: fat)
    }
}

struct DailyGoalDomain: Equatable {

    // MARK: - Properties

    var validFrom: String
    var kcalGoal: Double?
    var macroMode: GoalMacroMode = .percent
    var carbsPercent: Double?
    var proteinPercent: Double?
    var fatPercent: Double?
    var carbsGrams: Double?
    var proteinGrams: Double?
    var fatGrams: Double?
    var sex: GoalSex?
    var birthYear: Int?
    var heightCm: Double?
    var weightKg: Double?
    var activityLevel: GoalActivityLevel?

    var isActive: Bool { kcalGoal != nil }

    var hasProfile: Bool {
        sex != nil
            || birthYear != nil
            || heightCm != nil
            || weightKg != nil
            || activityLevel != nil
    }

    var macroGrams: GoalMacroGrams? {
        guard let kcalGoal else { return nil }
        switch macroMode {
        case .percent:
            guard
                let carbsPercent,
                let proteinPercent,
                let fatPercent
            else { return nil }
            return GoalMacroGrams(
                protein: EnergyKt.gramsFromKcal(kcal: kcalGoal, percent: proteinPercent, isFat: false),
                carbs: EnergyKt.gramsFromKcal(kcal: kcalGoal, percent: carbsPercent, isFat: false),
                fat: EnergyKt.gramsFromKcal(kcal: kcalGoal, percent: fatPercent, isFat: true)
            )
        case .grams:
            guard
                let carbsGrams,
                let proteinGrams,
                let fatGrams
            else { return nil }
            return GoalMacroGrams(protein: proteinGrams, carbs: carbsGrams, fat: fatGrams)
        }
    }

    // MARK: - Functions

    static func ended(validFrom: String) -> DailyGoalDomain {
        DailyGoalDomain(validFrom: validFrom)
    }
}

struct DailyGoalHistory: Equatable {

    // MARK: - Properties

    let entries: [DailyGoalDomain]

    static let empty = DailyGoalHistory(entries: [])

    // MARK: - Functions

    func goal(onDayKey dayKey: String) -> DailyGoalDomain? {
        guard
            let latest = latestEntry(onDayKey: dayKey),
            latest.isActive
        else { return nil }
        return latest
    }

    func latestEntry(onDayKey dayKey: String) -> DailyGoalDomain? {
        entries
            .filter { $0.validFrom <= dayKey }
            .max { $0.validFrom < $1.validFrom }
    }

    func latestProfileEntry(onDayKey dayKey: String) -> DailyGoalDomain? {
        entries
            .filter { $0.validFrom <= dayKey && $0.hasProfile }
            .max { $0.validFrom < $1.validFrom }
    }

    func replacing(_ entry: DailyGoalDomain) -> DailyGoalHistory {
        let others = entries.filter { $0.validFrom < entry.validFrom }
        return DailyGoalHistory(entries: (others + [entry]).sorted { $0.validFrom < $1.validFrom })
    }
}

struct GoalVariant: Equatable, Identifiable {

    // MARK: - Properties

    let percentChange: Int
    let kcal: Int
    let isRaisedToBasalRate: Bool

    var id: Int { percentChange }
}

struct GoalEstimate: Equatable {

    // MARK: - Properties

    let basalRate: Int
    let expenditure: Int
    let variants: [GoalVariant]

    static let percentChanges = GoalsKt.goalPercentChanges.map(\.intValue)

    // MARK: - Init

    init?(sex: GoalSex?, birthYear: Int?, heightCm: Double?, weightKg: Double?, activityLevel: GoalActivityLevel?, currentYear: Int) {
        guard
            let sex,
            let birthYear,
            let heightCm,
            let weightKg,
            let activityLevel
        else { return nil }
        let bmr = EnergyKt.basalMetabolicRate(isMale: sex == .male, weightKg: weightKg, heightCm: heightCm, ageYears: Int32(currentYear - birthYear))
        let expenditure = bmr * activityLevel.factor
        basalRate = Int(bmr.rounded())
        self.expenditure = Int(expenditure.rounded())
        variants = GoalsKt.goalVariants(basalRate: bmr, expenditure: expenditure).map {
            GoalVariant(percentChange: Int($0.percentChange), kcal: Int($0.kcal), isRaisedToBasalRate: $0.isRaisedToBasalRate)
        }
    }
}

enum GoalLimits {
    static let minAge = 18
    static let maxAge = 99
    static let heightRangeCm = 120...230
}
