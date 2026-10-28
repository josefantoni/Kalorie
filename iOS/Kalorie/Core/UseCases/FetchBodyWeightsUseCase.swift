//
//  FetchBodyWeightsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

protocol FetchBodyWeightsUseCaseProtocol {
    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain]
    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain]
    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain]
    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain]
}

struct FetchBodyWeightsUseCase: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        try await load(upTo: day, limit: Constants.Firestore.bodyWeightRecentLimit)
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let weights: [BodyWeightDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.bodyWeights(userId: userId),
            where: Constants.Firestore.Field.id,
            isGreaterThan: BodyWeightDayKey.make(from: day),
            orderByAscending: Constants.Firestore.bodyWeightLaterLimit
        )
        return weights.map { $0.asDomain() }
    }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] {
        let calendar = Calendar.current
        let dayCount = (calendar.dateComponents([.day], from: calendar.startOfDay(for: from), to: calendar.startOfDay(for: to)).day ?? 0) + 1
        return try await load(upTo: to, limit: dayCount + 1)
    }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let weights: [BodyWeightDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.bodyWeights(userId: userId),
            where: Constants.Firestore.Field.id,
            isGreaterThanOrEqualTo: String(format: "%04d-01-01", year),
            isLessThanOrEqualTo: String(format: "%04d-12-31", year)
        )
        return weights.map { $0.asDomain() }
    }

    // MARK: - Private

    private func load(upTo day: Date, limit: Int) async throws -> [BodyWeightDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let weights: [BodyWeightDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.bodyWeights(userId: userId),
            where: Constants.Firestore.Field.id,
            isLessThanOrEqualTo: BodyWeightDayKey.make(from: day),
            orderByDescending: limit
        )
        return weights.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchBodyWeightsUseCaseFake: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    var stubbedWeights: [BodyWeightDomain] = []
    var stubbedLaterWeights: [BodyWeightDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedLaterWeights
    }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights
    }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights.filter { $0.dayKey.hasPrefix(String(format: "%04d-", year)) }
    }
}
#endif
