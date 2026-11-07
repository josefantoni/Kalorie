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

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func callAsFunction(itemId: String, photo: Data) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let path = PhotoSuggestionDomain.storagePath(itemId: itemId, userId: userId)
        let existing: PhotoSuggestionDTO? = try await dataProvider.loadFromServerAsync(
            id: PhotoSuggestionDomain.id(itemId: itemId, userId: userId),
            from: Constants.Firestore.photoSuggestions
        )
        guard existing == nil else { return }
        // The rules deny overwriting, and a previous attempt may have uploaded the file and then failed
        // on the Firestore write — so the fixed path is cleared first.
        do {
            try await storageProvider.deleteAsync(path: path)
        } catch {
            Log.warning(error, category: Constants.LogCategory.storage)
        }
        let photoURL = try await storageProvider.uploadAsync(
            data: photo,
            path: path,
            contentType: Constants.Storage.photoContentType
        )
        let dto = PhotoSuggestionDTO(itemId: itemId, suggestedBy: userId, photoURL: photoURL, suggestedAt: .now)
        let documentId = PhotoSuggestionDomain.id(itemId: itemId, userId: userId)
        do {
            try await dataProvider.setAsync(dto, id: documentId, in: Constants.Firestore.photoSuggestions)
        } catch {
            let concurrent: PhotoSuggestionDTO? = try? await dataProvider.loadFromServerAsync(
                id: documentId,
                from: Constants.Firestore.photoSuggestions
            )
            if concurrent == nil {
                await deleteUploadedFileQuietly(path: path)
            }
            throw error
        }
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
