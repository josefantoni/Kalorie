//
//  SaveBodyWeightUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

enum BodyWeightError: Error, Equatable {
    case outOfRange
    case futureDay
    case beforeFirstYear
}

protocol SaveBodyWeightUseCaseProtocol {
    func callAsFunction(kilograms: Double, for day: Date) async throws
}

struct SaveBodyWeightUseCase: SaveBodyWeightUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let now: () -> Date

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        now: @escaping () -> Date = Date.init
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.now = now
    }

    // MARK: - Functions

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        guard (BodyWeightUnit.minKilograms...BodyWeightUnit.maxKilograms).contains(kilograms) else {
            throw BodyWeightError.outOfRange
        }
        let dayKey = BodyWeightDayKey.make(from: day)
        guard dayKey <= BodyWeightDayKey.make(from: now()) else { throw BodyWeightError.futureDay }
        guard dayKey >= Constants.BodyWeight.firstDayKey else { throw BodyWeightError.beforeFirstYear }
        try await dataProvider.setAsync(
            BodyWeightDTO(weight: BodyWeightDomain(dayKey: dayKey, kilograms: kilograms)),
            id: dayKey,
            in: Constants.Firestore.bodyWeights(userId: userId)
        )
    }
}

#if DEBUG
struct SaveBodyWeightUseCaseFake: SaveBodyWeightUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
