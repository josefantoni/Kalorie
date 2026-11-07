//
//  ApprovePhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

enum ApprovePhotoSuggestionError: Error {
    case itemMissing
    case itemAlreadyHasPhoto
}

protocol ApprovePhotoSuggestionUseCaseProtocol {
    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws
}

struct ApprovePhotoSuggestionUseCase: ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol
    private let updateFoodItem: any UpdateFoodItemUseCaseProtocol
    private let remover: PhotoSuggestionRemover

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol,
        updateFoodItem: any UpdateFoodItemUseCaseProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.downloadFoodPhoto = downloadFoodPhoto
        self.updateFoodItem = updateFoodItem
        self.remover = PhotoSuggestionRemover(dataProvider: dataProvider, storageProvider: storageProvider)
    }

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        guard let item = try await fetchFoodItemByBarcode(barcode: suggestion.itemId) else {
            throw ApprovePhotoSuggestionError.itemMissing
        }
        guard item.photoURL == nil else { throw ApprovePhotoSuggestionError.itemAlreadyHasPhoto }
        // The suggestion folder is private to its author, so the catalogue gets its own copy under
        // catalogPhotos (UpdateFoodItemUseCase uploads it) rather than a link into photoSuggestions.
        let photoData = try await downloadFoodPhoto(url: suggestion.photoURL)
        try await updateFoodItem(item, previouslyLoaded: item, photo: .local(photoData), validate: false)
        await removeAllSuggestions(for: suggestion.itemId)
    }

    // MARK: - Private

    // Other users' suggestions for the same item would otherwise wait forever: the item has a photo now.
    private func removeAllSuggestions(for itemId: String) async {
        do {
            let documentIds = try await dataProvider.loadDocumentIdsAsync(
                from: Constants.Firestore.photoSuggestions,
                where: Constants.Firestore.Field.itemId,
                isEqualTo: itemId
            )
            for documentId in documentIds {
                guard let ids = PhotoSuggestionDomain.ids(fromDocumentId: documentId) else { continue }
                do {
                    try await remover.remove(itemId: ids.itemId, userId: ids.userId)
                } catch {
                    Log.error(error, category: Constants.LogCategory.moderation)
                }
            }
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
        }
    }
}

#if DEBUG
struct ApprovePhotoSuggestionUseCaseFake: ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
