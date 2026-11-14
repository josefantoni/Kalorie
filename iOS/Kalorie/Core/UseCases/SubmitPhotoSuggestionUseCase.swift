//
//  SubmitPhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol SubmitPhotoSuggestionUseCaseProtocol {
    func callAsFunction(itemId: String, photo: Data) async throws
}

struct SubmitPhotoSuggestionUseCase: SubmitPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let storageProvider: any StorageDataProviderProtocol
    private let notificationsStore: any UserNotificationsStoreProtocol
    private let locale: String

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol,
        notificationsStore: any UserNotificationsStoreProtocol,
        locale: String = TicketLocale.current()
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.storageProvider = storageProvider
        self.notificationsStore = notificationsStore
        self.locale = locale
    }

    // MARK: - Functions

    func callAsFunction(itemId: String, photo: Data) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let ticketId = try dataProvider.newDocumentId(in: Constants.Firestore.photoSuggestions)
        let path = "\(Constants.Storage.photoSuggestionsFolder)/\(userId)/\(itemId)/\(ticketId).jpg"
        let photoURL = try await storageProvider.uploadAsync(
            data: photo,
            path: path,
            contentType: Constants.Storage.photoContentType
        )
        let dto = PhotoSuggestionCreateDTO(
            itemId: itemId,
            suggestedBy: userId,
            photoURL: photoURL,
            suggestedAt: .now,
            locale: locale
        )
        do {
            try await dataProvider.setAsync(dto, id: ticketId, in: Constants.Firestore.photoSuggestions)
        } catch {
            await deleteUploadedFileQuietly(path: path)
            throw error
        }
        notificationsStore.mayHaveUnread = true
        notificationsStore.lastSubmittedAt = .now
    }

    // MARK: - Private

    private func deleteUploadedFileQuietly(path: String) async {
        do {
            try await storageProvider.deleteAsync(path: path)
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
        }
    }
}

#if DEBUG
struct SubmitPhotoSuggestionUseCaseFake: SubmitPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(itemId: String, photo: Data) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
