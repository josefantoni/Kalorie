//
//  FoodConsumedDetailViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class FoodConsumedDetailViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onAppear_whenCatalogueItemNoLongerResolves_disablesAddingButKeepsButtonVisible() async {
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil))

        await sut.onAppear()

        XCTAssertFalse(sut.isFavourite)
        XCTAssertFalse(sut.canToggleFavourite, "with no catalogue item to snapshot, adding must stay disabled rather than crash or write garbage")
    }

    @MainActor
    func test_onFavouriteToggled_whenAlreadyFavouriteAndCatalogueItemMissing_stillAllowsUnfavouriting() async {
        let sut = makeSUT(
            isFavouriteFood: IsFavouriteFoodUseCaseFake(stubbedResult: true),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil)
        )
        await sut.onAppear()
        XCTAssertTrue(sut.isFavourite)
        XCTAssertTrue(sut.canToggleFavourite, "removal needs only the id, so it must stay possible even when the catalogue item vanished")

        await sut.onFavouriteToggled()

        XCTAssertFalse(sut.isFavourite)
        XCTAssertNil(sut.alertItem)
    }

    // MARK: - Helpers

    private func makeSUT(
        food: FoodConsumedDomain? = nil,
        updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol = UpdateFoodConsumedUseCaseFake(),
        isFavouriteFood: any IsFavouriteFoodUseCaseProtocol = IsFavouriteFoodUseCaseFake(),
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        onFoodUpdated: @escaping () -> Void = {}
    ) -> FoodConsumedDetailViewModel {
        let sut = FoodConsumedDetailViewModel(
            food: food ?? makeFood(),
            updateFoodConsumed: updateFoodConsumed,
            isFavouriteFood: isFavouriteFood,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            onFoodUpdated: onFoodUpdated
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "FoodConsumedDetailViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeFood(foodItemId: String = "12345") -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "1",
            foodItemId: foodItemId,
            czName: "Ovesné vločky",
            engName: "Oats",
            weight: 80,
            date: .now,
            calories: 295,
            protein: 10,
            carbohydrate: 52,
            carbohydrateSugar: 8,
            fat: 5,
            fatUnsaturated: 2,
            fiber: 6,
            salt: 0.1
        )
    }
}
