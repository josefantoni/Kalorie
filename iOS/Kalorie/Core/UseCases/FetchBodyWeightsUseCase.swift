//
//  FetchBodyWeightsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

protocol FetchBodyWeightsUseCaseProtocol {
    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain]
    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain]
    func callAsFunction() async throws -> [BodyWeightDomain]
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

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] {
        let calendar = Calendar.current
        let dayCount = (calendar.dateComponents([.day], from: calendar.startOfDay(for: from), to: calendar.startOfDay(for: to)).day ?? 0) + 1
        return try await load(upTo: to, limit: dayCount + 1)
    }

    func callAsFunction() async throws -> [BodyWeightDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let weights: [BodyWeightDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.bodyWeights(userId: userId)
        )
        return weights.map { $0.asDomain() }.sorted { $0.dayKey > $1.dayKey }
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
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights
    }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights
    }

    func callAsFunction() async throws -> [BodyWeightDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedWeights
    }
}
#endif
