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
    @Published var alertItem: AlertItem?

    private let onMealTypesChanged: () -> Void
    private let createMealType: any CreateMealTypeUseCaseProtocol
    private let deleteMealType: any DeleteMealTypeUseCaseProtocol
    private let updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        onMealTypesChanged: @escaping () -> Void = {},
        createMealType: any CreateMealTypeUseCaseProtocol,
        deleteMealType: any DeleteMealTypeUseCaseProtocol,
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol
    ) {
        self.mealTypes = mealTypes
        self.onMealTypesChanged = onMealTypesChanged
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
            onMealTypesChanged()
        } catch CreateMealTypeError.emptyName {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorEmptyName)
        } catch CreateMealTypeError.duplicateName {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorDuplicateName)
        } catch CreateMealTypeError.timeConflict {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorTimeConflict)
        } catch CreateMealTypeError.durationTooShort {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorDurationTooShort)
        } catch {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorUnexpected)
        }
    }

    @MainActor
    func onDelete(at index: Int) async {
        guard mealTypes.count > 1 else {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorLastMealType)
            return
        }
        state = .loading
        defer { state = .loaded }
        let mealType = mealTypes[index]
        do {
            try await deleteMealType(mealType)
            mealTypes.removeAll { $0.id == mealType.id }
            onMealTypesChanged()
        } catch {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorDeleteError)
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
        do {
            try await updateMealTypeTimes(mealTypes)
            onMealTypesChanged()
        } catch {
            alertItem = AlertItem(title: L10n.MealTypeSheet.errorUnexpected)
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
