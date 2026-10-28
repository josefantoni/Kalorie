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

    static let poundsPerKilogram = 1 / Constants.BodyWeight.kilogramsPerPound
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
        case .lb: value * Constants.BodyWeight.kilogramsPerPound
        }
    }
}

struct BodyWeightDomain: Equatable {

    // MARK: - Properties

    let dayKey: String
    let kilograms: Double
}

struct BodyWeightWindow: Equatable {

    // MARK: - Properties

    let earlier: [BodyWeightDomain]
    let later: [BodyWeightDomain]

    static let empty = BodyWeightWindow(earlier: [], later: [])

    var chronological: [BodyWeightDomain] { earlier.reversed() + later }
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

    static func year(of date: Date, timeZone: TimeZone = .current) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar.component(.year, from: date)
    }

    static func date(from key: String, timeZone: TimeZone = .current) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2], hour: 12))
    }
}

extension BodyWeightUnit {

    // MARK: - Properties

    var symbol: String { rawValue }

    var rangeInTenths: ClosedRange<Int> {
        switch self {
        case .kg: 300...3000
        case .lb: 662...6613
        }
    }

    // MARK: - Functions

    func formatted(kilograms: Double) -> String {
        let value = display(kilograms: kilograms)
        return "\(value.formatted(.number.precision(.fractionLength(1)))) \(symbol)"
    }

    func tenths(kilograms: Double) -> Int {
        min(max(Int((display(kilograms: kilograms) * 10).rounded()), rangeInTenths.lowerBound), rangeInTenths.upperBound)
    }
}
