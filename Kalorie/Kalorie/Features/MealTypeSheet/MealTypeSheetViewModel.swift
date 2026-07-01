//
//  MealTypeSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation

final class MealTypeSheetViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loaded
    @Published var mealTypes: [MealTypeDomain]
    @Published var newMealName = ""
    @Published var newMealStart = Date.now
    @Published var newMealEnd = Date.now
    @Published var isAddFormVisible = false
    @Published var showingAlert = false
    @Published var alertTitle = ""

    private let createMealType: any CreateMealTypeUseCaseProtocol
    private let deleteMealType: any DeleteMealTypeUseCaseProtocol
    private let updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        createMealType: any CreateMealTypeUseCaseProtocol,
        deleteMealType: any DeleteMealTypeUseCaseProtocol,
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol
    ) {
        self.mealTypes = mealTypes
        self.createMealType = createMealType
        self.deleteMealType = deleteMealType
        self.updateMealTypeTimes = updateMealTypeTimes
    }

    // MARK: - Functions

    @MainActor
    func onCreateMealType() async {
        state = .loading
        defer { state = .loaded }
        do {
            let newMeal = try await createMealType(
                name: newMealName,
                startTime: newMealStart,
                endTime: newMealEnd,
                existingMealTypes: mealTypes
            )
            mealTypes.append(newMeal)
            mealTypes.sort { $0.startTime < $1.startTime }
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
        } catch CreateMealTypeError.durationTooShort {
            alertTitle = L10n.MealTypeSheet.errorDurationTooShort
            showingAlert = true
        } catch {
            alertTitle = L10n.MealTypeSheet.errorUnexpected
            showingAlert = true
        }
    }

    @MainActor
    func onDelete(at index: Int) async {
        guard mealTypes.count > 1 else {
            alertTitle = L10n.MealTypeSheet.errorLastMealType
            showingAlert = true
            return
        }
        state = .loading
        defer { state = .loaded }
        let mealType = mealTypes[index]
        do {
            try await deleteMealType(mealType)
            mealTypes.removeAll { $0.id == mealType.id }
        } catch {
            alertTitle = L10n.MealTypeSheet.errorDeleteError
            showingAlert = true
        }
    }

    @MainActor
    func onMove(from source: IndexSet, to destination: Int) {
        let originalTimes = mealTypes.map { (startTime: $0.startTime, endTime: $0.endTime) }
        mealTypes.move(fromOffsets: source, toOffset: destination)
        for index in mealTypes.indices {
            let (startTime, endTime) = originalTimes[index]
            mealTypes[index] = MealTypeDomain(
                id: mealTypes[index].id,
                name: mealTypes[index].name,
                startTime: startTime,
                endTime: endTime
            )
        }
    }

    @MainActor
    func onSaveReorder() async {
        state = .loading
        defer { state = .loaded }
        for mealType in mealTypes {
            do {
                try await updateMealTypeTimes(mealType)
            } catch {
                alertTitle = L10n.MealTypeSheet.errorUnexpected
                showingAlert = true
                return
            }
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
