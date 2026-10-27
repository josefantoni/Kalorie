//
//  BodyWeightModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

enum BodyWeightUnit: String, Codable, CaseIterable {
    case kg
    case lb

    // MARK: - Properties

    static let poundsPerKilogram = 1 / 0.45359237
    static let minKilograms = 30.0
    static let maxKilograms = 300.0

    // MARK: - Functions

    func display(kilograms: Double) -> Double {
        switch self {
        case .kg: kilograms
        case .lb: kilograms * Self.poundsPerKilogram
        }
    }

    func kilograms(from value: Double) -> Double {
        switch self {
        case .kg: value
        case .lb: value * 0.45359237
        }
    }
}

struct BodyWeightDomain: Equatable {

    // MARK: - Properties

    let dayKey: String
    let kilograms: Double
}

struct AppSettingsDomain: Equatable {

    // MARK: - Properties

    var showBodyWeight: Bool
    var bodyWeightUnit: BodyWeightUnit

    static let defaults = AppSettingsDomain(showBodyWeight: true, bodyWeightUnit: .kg)
}

enum BodyWeightDayKey {

    // MARK: - Functions

    static func make(from date: Date, timeZone: TimeZone = .current) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}
