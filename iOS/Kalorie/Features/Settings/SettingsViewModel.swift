//
//  SettingsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation

final class SettingsViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loaded
    @Published var mealTypes: [MealTypeDomain]
    @Published private(set) var hasPendingReorder = false
    @Published var newMealName = ""
    @Published var newMealStart = Date.now
    @Published var newMealEnd = Date.now
    @Published var isAddFormVisible = false
    @Published var isExportPushed = false
    @Published private(set) var isMaintainer = false
    @Published var alertItem: AlertItem?

    private let onMealTypesChanged: () -> Void
    private let createMealType: any CreateMealTypeUseCaseProtocol
    private let deleteMealType: any DeleteMealTypeUseCaseProtocol
    private let updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol
    private let fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        onMealTypesChanged: @escaping () -> Void = {},
        createMealType: any CreateMealTypeUseCaseProtocol,
        deleteMealType: any DeleteMealTypeUseCaseProtocol,
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol,
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol
    ) {
        self.mealTypes = mealTypes
        self.onMealTypesChanged = onMealTypesChanged
        self.createMealType = createMealType
        self.deleteMealType = deleteMealType
        self.updateMealTypeTimes = updateMealTypeTimes
        self.fetchMaintainerClaim = fetchMaintainerClaim
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        do {
            isMaintainer = try await fetchMaintainerClaim()
        } catch {
            Log.warning(error, category: Constants.LogCategory.settings)
        }
    }

    @MainActor
    func onCreateMealType() async {
        state = .loading
        defer { state = .loaded }
        do {
            let newMeal = try await createMealType(
                name: newMealName,
                startMinutes: Int(newMealStart.minutesSinceMidnight),
                endMinutes: Int(newMealEnd.minutesSinceMidnight),
                existingMealTypes: mealTypes
            )
            mealTypes.append(newMeal)
            mealTypes.sort { $0.startMinutes < $1.startMinutes }
            isAddFormVisible = false
            newMealName = ""
            onMealTypesChanged()
        } catch CreateMealTypeError.emptyName {
            alertItem = AlertItem(title: L10n.Settings.errorEmptyName)
        } catch CreateMealTypeError.duplicateName {
            alertItem = AlertItem(title: L10n.Settings.errorDuplicateName)
        } catch CreateMealTypeError.timeConflict {
            alertItem = AlertItem(title: L10n.Settings.errorTimeConflict)
        } catch CreateMealTypeError.durationTooShort {
            alertItem = AlertItem(title: L10n.Settings.errorDurationTooShort)
        } catch {
            Log.error(error, category: Constants.LogCategory.settings)
            alertItem = AlertItem(title: L10n.Settings.errorUnexpected)
        }
    }

    @MainActor
    func onDelete(at index: Int) async {
        guard mealTypes.count > 1 else {
            alertItem = AlertItem(title: L10n.Settings.errorLastMealType)
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
            Log.error(error, category: Constants.LogCategory.settings)
            alertItem = AlertItem(title: L10n.Settings.errorDeleteError)
        }
    }

    @MainActor
    func onMove(from source: IndexSet, to destination: Int) {
        let originalTimes = mealTypes.map { (startMinutes: $0.startMinutes, endMinutes: $0.endMinutes) }
        mealTypes.move(fromOffsets: source, toOffset: destination)
        for index in mealTypes.indices {
            let (startMinutes, endMinutes) = originalTimes[index]
            mealTypes[index] = MealTypeDomain(
                id: mealTypes[index].id,
                name: mealTypes[index].name,
                startMinutes: startMinutes,
                endMinutes: endMinutes
            )
        }
        hasPendingReorder = true
    }

    @MainActor
    func onSaveReorder() async {
        guard hasPendingReorder else { return }
        state = .loading
        hasPendingReorder = false
        defer { state = .loaded }
        do {
            try await updateMealTypeTimes(mealTypes)
            onMealTypesChanged()
        } catch {
            Log.error(error, category: Constants.LogCategory.settings)
            alertItem = AlertItem(title: L10n.Settings.errorUnexpected)
        }
    }

    func onShowAddForm() {
        guard
            let latestEndMinutes = mealTypes.map({ $0.endMinutes }).max(),
            let possibleStart = Calendar.current.date(
                bySettingHour: latestEndMinutes / 60,
                minute: latestEndMinutes % 60,
                second: 0,
                of: .now
            )
        else {
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
