//
//  DailyGoalsUseCases.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import Foundation

protocol FetchDailyGoalsUseCaseProtocol {
    func callAsFunction() async throws -> DailyGoalHistory
}

struct FetchDailyGoalsUseCase: FetchDailyGoalsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> DailyGoalHistory {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto: DailyGoalsDTO? = try await dataProvider.loadAsync(
            id: Constants.Firestore.goalsDocumentId,
            from: Constants.Firestore.settings(userId: userId)
        )
        return dto?.asDomain() ?? .empty
    }
}

enum DailyGoalError: Error, Equatable {
    case invalidKcal
    case invalidMacros
}

protocol SaveDailyGoalUseCaseProtocol {
    func callAsFunction(_ goal: DailyGoalDomain) async throws -> DailyGoalHistory
}

struct SaveDailyGoalUseCase: SaveDailyGoalUseCaseProtocol {

    // MARK: - Properties

    static let kcalRange = 500.0...10000.0
    static let macroGramsRange = 0.0...1000.0
    static let macroPercentRange = 0.0...100.0

    private let fetchDailyGoals: any FetchDailyGoalsUseCaseProtocol
    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let now: () -> Date

    // MARK: - Init

    init(
        fetchDailyGoals: any FetchDailyGoalsUseCaseProtocol,
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        now: @escaping () -> Date = Date.init
    ) {
        self.fetchDailyGoals = fetchDailyGoals
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.now = now
    }

    // MARK: - Functions

    func callAsFunction(_ goal: DailyGoalDomain) async throws -> DailyGoalHistory {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        var entry = goal
        entry.validFrom = BodyWeightDayKey.make(from: now())
        if let kcalGoal = entry.kcalGoal {
            guard Self.kcalRange.contains(kcalGoal) else { throw DailyGoalError.invalidKcal }
            guard entry.macroGrams != nil else { throw DailyGoalError.invalidMacros }
            let macroValues = entry.macroMode == .percent
                ? [entry.carbsPercent, entry.proteinPercent, entry.fatPercent]
                : [entry.carbsGrams, entry.proteinGrams, entry.fatGrams]
            let macroRange = entry.macroMode == .percent ? Self.macroPercentRange : Self.macroGramsRange
            guard macroValues.allSatisfy({ macroRange.contains($0 ?? 0) }) else { throw DailyGoalError.invalidMacros }
            if entry.macroMode == .percent {
                let sum = (entry.carbsPercent ?? 0) + (entry.proteinPercent ?? 0) + (entry.fatPercent ?? 0)
                guard abs(sum - 100) < 0.001 else { throw DailyGoalError.invalidMacros }
            }
        }
        let current = try await fetchDailyGoals()
        if
            entry.kcalGoal == nil,
            !entry.hasProfile,
            let sameDay = current.entries.first(where: { $0.validFrom == entry.validFrom }),
            sameDay.hasProfile
        {
            entry.sex = sameDay.sex
            entry.birthYear = sameDay.birthYear
            entry.heightCm = sameDay.heightCm
            entry.weightKg = sameDay.weightKg
            entry.activityLevel = sameDay.activityLevel
        }
        let history = current.replacing(entry)
        try await dataProvider.mergeAsync(
            DailyGoalsDTO(history: history),
            id: Constants.Firestore.goalsDocumentId,
            in: Constants.Firestore.settings(userId: userId)
        )
        return history
    }
}

#if DEBUG
struct FetchDailyGoalsUseCaseFake: FetchDailyGoalsUseCaseProtocol {

    // MARK: - Properties

    var stubbedHistory = DailyGoalHistory.empty
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> DailyGoalHistory {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedHistory
    }
}

struct SaveDailyGoalUseCaseFake: SaveDailyGoalUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ goal: DailyGoalDomain) async throws -> DailyGoalHistory {
        if shouldThrow { throw URLError(.unknown) }
        return DailyGoalHistory(entries: [goal])
    }
}
#endif
