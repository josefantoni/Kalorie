//
//  DailyGoalsViewModelFakes.swift
//  KalorieTests
//
//  Created by Josef Antoni on 28.11.2026.
//

import XCTest
@testable import Kalorie

final class SuspendedSaveDailyGoal: SaveDailyGoalUseCaseProtocol {
    private(set) var goals: [DailyGoalDomain] = []
    private var continuation: CheckedContinuation<Void, Never>?
    private var requestWaiter: CheckedContinuation<Void, Never>?

    func waitUntilRequested() async {
        guard goals.isEmpty else { return }
        await withCheckedContinuation { requestWaiter = $0 }
    }

    func resume() {
        continuation?.resume()
        continuation = nil
    }

    func callAsFunction(_ goal: DailyGoalDomain) async throws -> DailyGoalHistory {
        goals.append(goal)
        if goals.count == 1 {
            requestWaiter?.resume()
            requestWaiter = nil
            await withCheckedContinuation { continuation = $0 }
        }
        return DailyGoalHistory(entries: [goal])
    }
}

final class SaveBodyWeightRecorder: SaveBodyWeightUseCaseProtocol {
    private(set) var loggedKilograms: [Double] = []

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        loggedKilograms.append(kilograms)
    }
}

final class SuspendedFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {
    private let weights: [BodyWeightDomain]
    private var continuation: CheckedContinuation<Void, Never>?
    private var requestWaiter: CheckedContinuation<Void, Never>?
    private var isRequested = false

    init(weights: [BodyWeightDomain]) {
        self.weights = weights
    }

    func waitUntilRequested() async {
        guard !isRequested else { return }
        await withCheckedContinuation { requestWaiter = $0 }
    }

    func resume() {
        continuation?.resume()
        continuation = nil
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        isRequested = true
        requestWaiter?.resume()
        requestWaiter = nil
        await withCheckedContinuation { continuation = $0 }
        return weights
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}
