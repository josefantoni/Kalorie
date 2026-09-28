//
//  CopyFoodsConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.09.2026.
//

import Foundation

enum CopyFoodsConsumedError: Error {
    case mealTypeNotFound
    case invalidTargetDate
}

protocol CopyFoodsConsumedUseCaseProtocol {
    func callAsFunction(
        _ foods: [FoodConsumedDomain],
        toDay day: Date,
        mealTypeId: String,
        mealTypes: [MealTypeDomain]
    ) async throws
}

struct CopyFoodsConsumedUseCase: CopyFoodsConsumedUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let now: () -> Date

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        now: @escaping () -> Date = { Date.now }
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.now = now
    }

    // MARK: - Functions

    func callAsFunction(
        _ foods: [FoodConsumedDomain],
        toDay day: Date,
        mealTypeId: String,
        mealTypes: [MealTypeDomain]
    ) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let target = try targetDate(day: day, mealTypeId: mealTypeId, mealTypes: mealTypes)
        let items = foods.enumerated().map { index, food in
            let dto = FoodConsumedDTO(
                food: food,
                mealTypeId: mealTypeId,
                id: UUID().uuidString,
                date: target.addingTimeInterval(TimeInterval(index))
            )
            return (item: dto, id: dto.id)
        }
        try await dataProvider.batchSetAsync(items, in: Constants.Firestore.foodConsumed(userId: userId))
    }

    // MARK: - Private

    private func targetDate(day: Date, mealTypeId: String, mealTypes: [MealTypeDomain]) throws -> Date {
        let calendar = Calendar.current
        let current = now()
        let isToday = calendar.isDate(day, inSameDayAs: current)
        guard
            let minutes = mealTypes.copyTargetMinutes(
                nowMinutes: isToday ? Int(current.minutesSinceMidnight) : nil,
                targetId: mealTypeId
            )
        else {
            throw CopyFoodsConsumedError.mealTypeNotFound
        }
        var components = calendar.dateComponents([.year, .month, .day], from: day)
        components.hour = minutes / 60
        components.minute = minutes % 60
        guard let date = calendar.date(from: components) else {
            throw CopyFoodsConsumedError.invalidTargetDate
        }
        return date
    }
}

#if DEBUG
struct CopyFoodsConsumedUseCaseFake: CopyFoodsConsumedUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(
        _ foods: [FoodConsumedDomain],
        toDay day: Date,
        mealTypeId: String,
        mealTypes: [MealTypeDomain]
    ) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
