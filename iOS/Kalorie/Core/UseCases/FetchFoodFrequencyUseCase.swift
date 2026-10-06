//
//  FetchFoodFrequencyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import Foundation

protocol FetchFoodFrequencyUseCaseProtocol {
    func callAsFunction() async throws -> [String: FoodFrequencyEntry]
}

struct FetchFoodFrequencyUseCase: FetchFoodFrequencyUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [String: FoodFrequencyEntry] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let collection = Constants.Firestore.stats(userId: userId)
        let document: FoodFrequencyDocumentDTO? = try await dataProvider.loadAsync(
            id: Constants.Firestore.foodFrequencyDocumentId,
            from: collection
        )
        guard let document else { return [:] }
        let ranked = document.entries.sorted {
            if $0.value.lastLoggedAt != $1.value.lastLoggedAt { return $0.value.lastLoggedAt > $1.value.lastLoggedAt }
            if $0.value.count != $1.value.count { return $0.value.count > $1.value.count }
            return $0.key < $1.key
        }
        let kept = ranked.prefix(Constants.Search.frequencyEntryLimit)
        let excessIds = ranked.dropFirst(Constants.Search.frequencyEntryLimit).map(\.key) + document.undecodableIds
        if !excessIds.isEmpty {
            do {
                try await dataProvider.deleteEntriesAsync(
                    ids: excessIds,
                    documentId: Constants.Firestore.foodFrequencyDocumentId,
                    in: collection
                )
            } catch {
                Log.warning(error, category: Constants.LogCategory.addFoodSheet)
            }
        }
        return Dictionary(uniqueKeysWithValues: kept.map { id, entry in
            (id, FoodFrequencyEntry(
                count: entry.count,
                lastLoggedAt: Date(timeIntervalSince1970: entry.lastLoggedAt),
                item: entry.item.asDomain()
            ))
        })
    }
}

#if DEBUG
struct FetchFoodFrequencyUseCaseFake: FetchFoodFrequencyUseCaseProtocol {

    // MARK: - Properties

    var stubbedEntries: [String: FoodFrequencyEntry] = [:]
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [String: FoodFrequencyEntry] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedEntries
    }
}
#endif
