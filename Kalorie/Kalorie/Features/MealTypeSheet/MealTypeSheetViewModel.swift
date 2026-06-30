//
//  MealTypeSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation

final class MealTypeSheetViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published var mealTypes: [MealTypeDomain]
    @Published var newMealName = ""
    @Published var newMealStart = Date.now
    @Published var newMealEnd = Date.now
    @Published var isAddFormVisible = false
    @Published var showingAlert = false
    @Published var alertTitle = ""

    private let createMealType: any CreateMealTypeUseCaseProtocol
    private let deleteMealType: any DeleteMealTypeUseCaseProtocol

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        createMealType: any CreateMealTypeUseCaseProtocol,
        deleteMealType: any DeleteMealTypeUseCaseProtocol
    ) {
        self.mealTypes = mealTypes
        self.createMealType = createMealType
        self.deleteMealType = deleteMealType
    }

    // MARK: - Functions

    func onCreateMealType() async {
        do {
            let newMeal = try await createMealType(
                name: newMealName,
                startTime: newMealStart,
                endTime: newMealEnd,
                existingMealTypes: mealTypes
            )
            mealTypes.append(newMeal)
            isAddFormVisible = false
            newMealName = ""
        } catch CreateMealTypeError.emptyName {
            alertTitle = L10n.MealTypeSheet.errorEmptyName
            showingAlert = true
        } catch CreateMealTypeError.duplicateName {
            alertTitle = L10n.MealTypeSheet.errorDuplicateName
            showingAlert = true
        } catch CreateMealTypeError.timeConflict {
            alertTitle = L10n.MealTypeSheet.errorTimeConflict
            showingAlert = true
        } catch {
            alertTitle = L10n.MealTypeSheet.errorUnexpected
            showingAlert = true
        }
    }

    func onDelete(at index: Int) async {
        let mealType = mealTypes[index]
        do {
            try await deleteMealType(mealType)
            mealTypes.removeAll { $0.id == mealType.id }
        } catch {
            alertTitle = L10n.MealTypeSheet.errorDeleteError
            showingAlert = true
        }
    }

    func onShowAddForm() {
        guard let possibleStart = mealTypes.map({ $0.endTime }).max() else {
            newMealStart = Date.now
            newMealEnd = Date.now.withAddedMinutes(minutes: 30)
            isAddFormVisible = true
            return
        }
        newMealStart = possibleStart
        newMealEnd = possibleStart.withAddedMinutes(minutes: 30)
        isAddFormVisible = true
    }
}
