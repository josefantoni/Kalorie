//
//  MyCreatedMealListViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

final class MyCreatedMealListViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var meals: [MyCreatedMealDomain] = []
    @Published var alertItem: AlertItem?
    @Published var isDeleteConfirmationVisible = false

    private var mealPendingDeletion: MyCreatedMealDomain?
    private let fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol
    private let deleteMyCreatedMeal: any DeleteMyCreatedMealUseCaseProtocol

    // MARK: - Init

    init(fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol, deleteMyCreatedMeal: any DeleteMyCreatedMealUseCaseProtocol) {
        self.fetchMyCreatedMeals = fetchMyCreatedMeals
        self.deleteMyCreatedMeal = deleteMyCreatedMeal
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        await reload()
    }

    @MainActor
    func onSaved() async {
        await reload()
    }

    func onDeleteRequested(_ meal: MyCreatedMealDomain) {
        mealPendingDeletion = meal
        isDeleteConfirmationVisible = true
    }

    @MainActor
    func onDeleteConfirmed() async {
        guard let meal = mealPendingDeletion else { return }
        mealPendingDeletion = nil
        let index = meals.firstIndex { $0.id == meal.id }
        meals.removeAll { $0.id == meal.id }
        do {
            try await deleteMyCreatedMeal(id: meal.id)
        } catch {
            if let index { meals.insert(meal, at: min(index, meals.count)) }
            alertItem = AlertItem(title: L10n.MyCreatedMeal.errorDeleteFailed)
        }
    }

    // MARK: - Private

    @MainActor
    private func reload() async {
        state = .loading
        do {
            meals = try await fetchMyCreatedMeals()
        } catch {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
        state = .loaded
    }
}
