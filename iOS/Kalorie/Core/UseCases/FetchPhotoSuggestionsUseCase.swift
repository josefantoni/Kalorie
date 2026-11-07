//
//  FetchPhotoSuggestionsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol FetchPhotoSuggestionsUseCaseProtocol {
    func callAsFunction() async throws -> [PhotoSuggestionDomain]
}

struct FetchPhotoSuggestionsUseCase: FetchPhotoSuggestionsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [PhotoSuggestionDomain] {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let dtos: [PhotoSuggestionDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.photoSuggestions,
            orderBy: Constants.Firestore.Field.suggestedAt,
            descending: false,
            limit: Constants.Firestore.reportsPageLimit
        )
        return dtos.compactMap { $0.asDomain() }
    }
}

#if DEBUG
struct FetchPhotoSuggestionsUseCaseFake: FetchPhotoSuggestionsUseCaseProtocol {

    // MARK: - Properties

    var stubbedSuggestions: [PhotoSuggestionDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [PhotoSuggestionDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedSuggestions
    }
}
#endif
