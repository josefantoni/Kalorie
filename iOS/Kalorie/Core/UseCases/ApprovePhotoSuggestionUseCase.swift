//
//  ApprovePhotoSuggestionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

enum ApprovePhotoSuggestionError: Error {
    case itemMissing
}

protocol ApprovePhotoSuggestionUseCaseProtocol {
    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws
}

struct ApprovePhotoSuggestionUseCase: ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol
    private let updateFoodItem: any UpdateFoodItemUseCaseProtocol
    private let remover: PhotoSuggestionRemover
    private let writer: TicketResolutionWriter

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol,
        updateFoodItem: any UpdateFoodItemUseCaseProtocol
    ) {
        self.authProvider = authProvider
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.downloadFoodPhoto = downloadFoodPhoto
        self.updateFoodItem = updateFoodItem
        self.remover = PhotoSuggestionRemover(storageProvider: storageProvider)
        self.writer = TicketResolutionWriter(dataProvider: dataProvider)
    }

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let trimmedResponse = try TicketResolutionWriter.validated(response)
        guard let item = try await fetchFoodItemByBarcode(barcode: suggestion.itemId) else {
            throw ApprovePhotoSuggestionError.itemMissing
        }
        // The suggestion folder is private to its author, so the catalogue gets its own copy under
        // catalogPhotos (UpdateFoodItemUseCase uploads it, and deletes the photo it replaces) rather
        // than a link into photoSuggestions.
        let photoData = try await downloadFoodPhoto(url: suggestion.photoURL)
        try await updateFoodItem(item, previouslyLoaded: item, photo: .local(photoData), validate: false)
        try await writer.write(
            status: .accepted,
            response: trimmedResponse,
            ticketId: suggestion.id,
            in: Constants.Firestore.photoSuggestions
        )
        await remover.deleteFileQuietly(at: suggestion.photoURL)
    }
}

#if DEBUG
struct ApprovePhotoSuggestionUseCaseFake: ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
