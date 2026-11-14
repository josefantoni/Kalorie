//
//  RejectPhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol RejectPhotoSuggestionUseCaseProtocol {
    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws
}

struct RejectPhotoSuggestionUseCase: RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let remover: PhotoSuggestionRemover
    private let writer: TicketResolutionWriter

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol
    ) {
        self.authProvider = authProvider
        self.remover = PhotoSuggestionRemover(storageProvider: storageProvider)
        self.writer = TicketResolutionWriter(dataProvider: dataProvider)
    }

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let trimmedResponse = try TicketResolutionWriter.validated(response)
        try await writer.write(
            status: .rejected,
            response: trimmedResponse,
            ticketId: suggestion.id,
            in: Constants.Firestore.photoSuggestions
        )
        await remover.deleteFileQuietly(at: suggestion.photoURL)
    }
}

#if DEBUG
struct RejectPhotoSuggestionUseCaseFake: RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
