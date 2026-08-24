//
//  MyCreatedMealListViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 20.08.2026.
//

import XCTest
@testable import Kalorie

final class MyCreatedMealListViewModelTests: XCTestCase {

    // MARK: - onAppear

    @MainActor
    func test_onAppear_loadsMeals() async {
        let sut = makeSUT(fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "1"), makeMeal(id: "2")]))
        await sut.onAppear()
        XCTAssertEqual(sut.meals.map(\.id), ["1", "2"])
        XCTAssertFalse(sut.state.isLoading)
    }

    @MainActor
    func test_onAppear_whenFetchFails_showsAlert() async {
        let sut = makeSUT(fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertNotNil(sut.alertItem)
        XCTAssertFalse(sut.state.isLoading)
    }

    // MARK: - onSaved

    @MainActor
    func test_onSaved_reloadsMeals() async {
        let fetchSpy = FetchMyCreatedMealsUseCaseSpy(stubbedMeals: [makeMeal(id: "1")])
        let sut = makeSUT(fetchMyCreatedMeals: fetchSpy)
        await sut.onAppear()
        XCTAssertEqual(sut.meals.count, 1)

        fetchSpy.stubbedMeals = [makeMeal(id: "1"), makeMeal(id: "2")]
        await sut.onSaved()
        XCTAssertEqual(sut.meals.map(\.id), ["1", "2"])
    }

    // MARK: - delete

    @MainActor
    func test_onDeleteConfirmed_removesRowOptimistically_andDeletes() async {
        let deleteUseCase = DeleteMyCreatedMealUseCaseFake()
        let sut = makeSUT(
            fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "1"), makeMeal(id: "2")]),
            deleteMyCreatedMeal: deleteUseCase
        )
        await sut.onAppear()

        sut.onDeleteRequested(sut.meals[0])
        XCTAssertTrue(sut.isDeleteConfirmationVisible)

        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.meals.map(\.id), ["2"])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteConfirmed_whenDeleteFails_restoresRowAndShowsAlert() async {
        let sut = makeSUT(
            fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "1"), makeMeal(id: "2")]),
            deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()

        sut.onDeleteRequested(sut.meals[0])
        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.meals.map(\.id), ["1", "2"], "a failed delete must restore the row at its original position")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteConfirmed_withoutAPendingRequest_doesNothing() async {
        let sut = makeSUT(fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "1")]))
        await sut.onAppear()

        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.meals.map(\.id), ["1"])
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol = FetchMyCreatedMealsUseCaseFake(),
        deleteMyCreatedMeal: any DeleteMyCreatedMealUseCaseProtocol = DeleteMyCreatedMealUseCaseFake()
    ) -> MyCreatedMealListViewModel {
        let sut = MyCreatedMealListViewModel(fetchMyCreatedMeals: fetchMyCreatedMeals, deleteMyCreatedMeal: deleteMyCreatedMeal)
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "MyCreatedMealListViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeMeal(id: String) -> MyCreatedMealDomain {
        MyCreatedMealDomain(
            id: id,
            name: "Kaše",
            ingredients: [
                MyCreatedMealIngredientDomain(
                    foodItemId: "12345",
                    czName: "Ovesné vločky",
                    engName: "Oats",
                    grams: 50,
                    nutrition: FoodNutritionValues(
                        energyKJ: 648,
                        caloriesPerHundredGrams: 155,
                        fat: 10,
                        fatSaturated: 3,
                        fatUnsaturatedFattyAcids: 3,
                        carbohydrate: 1,
                        carbohydratePureSugar: 0,
                        fiber: 0,
                        protein: 13,
                        salt: 0.3
                    )
                )
            ],
            createdAt: .now,
            updatedAt: .now
        )
    }
}

private final class FetchMyCreatedMealsUseCaseSpy: FetchMyCreatedMealsUseCaseProtocol {

    // MARK: - Properties

    var stubbedMeals: [MyCreatedMealDomain]

    // MARK: - Init

    init(stubbedMeals: [MyCreatedMealDomain]) {
        self.stubbedMeals = stubbedMeals
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [MyCreatedMealDomain] {
        stubbedMeals
    }
}
