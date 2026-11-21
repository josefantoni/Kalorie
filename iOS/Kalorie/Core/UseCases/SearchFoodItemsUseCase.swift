//
//  SearchFoodItemsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation
import TextKit

protocol SearchFoodItemsUseCaseProtocol {
    func callAsFunction(query: String, languageCode: String?) async throws -> [FoodItemDomain]
}

extension SearchFoodItemsUseCaseProtocol {
    func callAsFunction(query: String) async throws -> [FoodItemDomain] {
        try await callAsFunction(query: query, languageCode: nil)
    }
}

struct SearchFoodItemsUseCase: SearchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let languageCode: () -> String

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        languageCode: @escaping () -> String = { Locale.currentLanguageCode }
    ) {
        self.dataProvider = dataProvider
        self.languageCode = languageCode
    }

    // MARK: - Functions

    func callAsFunction(query: String, languageCode searchLanguageCode: String?) async throws -> [FoodItemDomain] {
        let language = searchLanguageCode ?? languageCode()
        guard query.trimmingCharacters(in: .whitespaces).count >= Constants.Search.minimumQueryLength else { return [] }
        let searchQuery = SearchTermsKt.searchQuery(query: query)
        let foldedQuery = searchQuery.folded
        let foldedLastWord = searchQuery.lastWord
        let (prefixFields, tokenFields) = searchFields(for: language)
        async let prefixResults = loadByPrefix(fields: prefixFields, prefix: foldedQuery)
        async let tokenResults = loadByToken(fields: tokenFields, token: foldedLastWord)
        let results = try await prefixResults + tokenResults
        var seen = Set<String>()
        return results
            .filter { seen.insert($0.id).inserted }
            .map { $0.asDomain() }
            .filter { SearchTermsKt.matchesSearchQuery(name: $0.displayName(languageCode: language), query: searchQuery) }
    }

    private func searchFields(for languageCode: String) -> (prefix: [String], token: [String]) {
        switch NameRulesKt.nameLanguage(languageCode: languageCode) {
        case "cs":
            return ([Constants.Firestore.Field.czNameFolded], [Constants.Firestore.Field.czNameSearchTerms])
        case "sk":
            return ([Constants.Firestore.Field.skNameFolded], [Constants.Firestore.Field.skNameSearchTerms])
        default:
            return ([Constants.Firestore.Field.engNameFolded], [Constants.Firestore.Field.engNameSearchTerms])
        }
    }

    private func loadByPrefix(fields: [String], prefix: String) async throws -> [FoodItemDTO] {
        try await withThrowingTaskGroup(of: (Int, [FoodItemDTO]).self) { group in
            for (index, field) in fields.enumerated() {
                group.addTask {
                    (index, try await dataProvider.loadAsync(
                        from: Constants.Firestore.foodItems,
                        where: field,
                        hasPrefix: prefix,
                        limit: 10
                    ))
                }
            }
            return try await group.reduce(into: [(Int, [FoodItemDTO])]()) { $0.append($1) }
                .sorted { $0.0 < $1.0 }
                .flatMap(\.1)
        }
    }

    private func loadByToken(fields: [String], token: String) async throws -> [FoodItemDTO] {
        try await withThrowingTaskGroup(of: (Int, [FoodItemDTO]).self) { group in
            for (index, field) in fields.enumerated() {
                group.addTask {
                    (index, try await dataProvider.loadAsync(
                        from: Constants.Firestore.foodItems,
                        where: field,
                        arrayContains: token,
                        limit: 10
                    ))
                }
            }
            return try await group.reduce(into: [(Int, [FoodItemDTO])]()) { $0.append($1) }
                .sorted { $0.0 < $1.0 }
                .flatMap(\.1)
        }
    }
}

#if DEBUG
struct SearchFoodItemsUseCaseFake: SearchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false
    var stubbedItems: [FoodItemDomain] = []

    // MARK: - Functions

    func callAsFunction(query: String, languageCode: String?) async throws -> [FoodItemDomain] {
        if shouldThrow { throw NSError(domain: "SearchFoodItemsUseCaseFake", code: 0) }
        return stubbedItems
    }
}
#endif
