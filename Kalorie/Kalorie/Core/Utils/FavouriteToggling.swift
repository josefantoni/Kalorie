//
//  FavouriteToggling.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

enum FavouriteToggleError: Error {
    case missingItem
}

protocol FavouriteToggling: AnyObject {
    var isFavourite: Bool { get set }
    var isTogglingFavourite: Bool { get set }
    var alertItem: AlertItem? { get set }
}

extension FavouriteToggling {

    @MainActor
    func toggleFavourite(
        item: FoodItemDomain?,
        removalId: String,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol,
        onToggled: ((Bool) -> Void)? = nil
    ) async {
        guard !isTogglingFavourite else { return }
        isTogglingFavourite = true
        defer { isTogglingFavourite = false }

        let newValue = !isFavourite
        isFavourite = newValue
        do {
            if newValue {
                guard let item else { throw FavouriteToggleError.missingItem }
                try await addFavouriteFood(item)
            } else {
                try await removeFavouriteFood(id: removalId)
            }
            onToggled?(newValue)
        } catch {
            Log.error(error, category: Constants.LogCategory.favourites)
            isFavourite = !newValue
            alertItem = AlertItem(title: L10n.AddFood.errorFavouriteFailed)
        }
    }
}
