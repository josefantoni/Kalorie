//
//  FoodPhotoSuggesting.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol FoodPhotoSuggesting: AnyObject {
    var photoPillState: FoodPhotoPillState { get set }
    var alertItem: AlertItem? { get set }
}

extension FoodPhotoSuggesting {

    // The pill is decided from the freshly fetched catalogue item, never from a favourite or frequency
    // snapshot: those keep a stale photo_url after an approval and would ask for a photo that exists.
    @MainActor
    func resolveCataloguePhotoPill(
        fetchedItem: FoodItemDomain?,
        fetchMyPhotoSuggestion: any FetchMyPhotoSuggestionUseCaseProtocol
    ) async {
        guard let fetchedItem else {
            photoPillState = .hidden
            return
        }
        if let url = fetchedItem.photoURL {
            photoPillState = .photo(url)
            return
        }
        do {
            let hasSuggestion = try await fetchMyPhotoSuggestion(itemId: fetchedItem.id)
            photoPillState = hasSuggestion ? .awaitingApproval : .empty
        } catch {
            Log.warning(error, category: Constants.LogCategory.storage)
            photoPillState = .hidden
        }
    }

    @MainActor
    func onPhotoSent(
        _ photo: Data,
        itemId: String,
        submitPhotoSuggestion: any SubmitPhotoSuggestionUseCaseProtocol
    ) async {
        do {
            try await submitPhotoSuggestion(itemId: itemId, photo: photo)
            photoPillState = .awaitingApproval
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
            alertItem = AlertItem(title: L10n.FoodPhoto.suggestionErrorSendFailed)
        }
    }
}
