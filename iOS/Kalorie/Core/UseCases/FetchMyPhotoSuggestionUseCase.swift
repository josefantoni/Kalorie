//
//  FetchMyPhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol FetchMyPhotoSuggestionUseCaseProtocol {
    func callAsFunction(itemId: String) async throws -> Bool
}

struct FetchMyPhotoSuggestionUseCase: FetchMyPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(itemId: String) async throws -> Bool {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [PhotoSuggestionDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.photoSuggestions,
            whereEqualTo: [
                (Constants.Firestore.Field.suggestedBy, userId),
                (Constants.Firestore.Field.itemId, itemId),
                (Constants.Firestore.Field.status, Constants.Firestore.pendingStatus)
            ],
            limit: 1
        )
        return !dtos.isEmpty
    }
}

#if DEBUG
struct FetchMyPhotoSuggestionUseCaseFake: FetchMyPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var hasSuggestion = false
    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(itemId: String) async throws -> Bool {
        if let errorToThrow { throw errorToThrow }
        return hasSuggestion
    }
}
#endif
