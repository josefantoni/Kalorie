//
//  RejectPhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol RejectPhotoSuggestionUseCaseProtocol {
    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws
}

struct RejectPhotoSuggestionUseCase: RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let remover: PhotoSuggestionRemover

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol
    ) {
        self.authProvider = authProvider
        self.remover = PhotoSuggestionRemover(dataProvider: dataProvider, storageProvider: storageProvider)
    }

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        try await remover.remove(itemId: suggestion.itemId, userId: suggestion.suggestedBy)
    }
}

#if DEBUG
struct RejectPhotoSuggestionUseCaseFake: RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
