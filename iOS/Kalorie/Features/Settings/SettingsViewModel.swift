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
    @Published var isSupportPushed = false
    @Published var isFeedbackMailUnavailableAlertPresented = false
    @Published private(set) var isMaintainer = false
    @Published var alertItem: AlertItem?
    @Published private(set) var appSettings: AppSettingsDomain
    let hasLoadedAppSettings: Bool

    var feedbackURL: URL? {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = Constants.Support.email
        components.queryItems = [URLQueryItem(name: "subject", value: L10n.Settings.buttonFeedback)]
        return components.url
    }

    private var persistedAppSettings: AppSettingsDomain
    private var lastSave: Task<Void, Never>?
    private let onMealTypesChanged: () -> Void
    private let onAppSettingsChanged: (AppSettingsDomain) -> Void
    private let saveAppSettings: any SaveAppSettingsUseCaseProtocol
    private let createMealType: any CreateMealTypeUseCaseProtocol
    private let deleteMealType: any DeleteMealTypeUseCaseProtocol
    private let updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol
    private let fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        appSettings: AppSettingsDomain,
        onMealTypesChanged: @escaping () -> Void = {},
        onAppSettingsChanged: @escaping (AppSettingsDomain) -> Void = { _ in },
        saveAppSettings: any SaveAppSettingsUseCaseProtocol,
        createMealType: any CreateMealTypeUseCaseProtocol,
        deleteMealType: any DeleteMealTypeUseCaseProtocol,
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol,
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol,
        hasLoadedAppSettings: Bool = true
    ) {
        self.mealTypes = mealTypes
        self.appSettings = appSettings
        self.persistedAppSettings = appSettings
        self.hasLoadedAppSettings = hasLoadedAppSettings
        self.onMealTypesChanged = onMealTypesChanged
        self.onAppSettingsChanged = onAppSettingsChanged
        self.saveAppSettings = saveAppSettings
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
    func onShowBodyWeightChanged(_ isShown: Bool) async {
        var updated = appSettings
        updated.showBodyWeight = isShown
        await apply(updated)
    }

    @MainActor
    func onBodyWeightUnitChanged(_ unit: BodyWeightUnit) async {
        var updated = appSettings
        updated.bodyWeightUnit = unit
        await apply(updated)
    }

    @MainActor
    private func apply(_ updated: AppSettingsDomain) async {
        guard updated != appSettings else { return }
        appSettings = updated
        let previousSave = lastSave
        let save = Task { @MainActor in
            await previousSave?.value
            await persist(updated)
        }
        lastSave = save
        await save.value
    }

    @MainActor
    private func persist(_ updated: AppSettingsDomain) async {
        do {
            try await saveAppSettings(updated)
            persistedAppSettings = updated
            onAppSettingsChanged(updated)
        } catch {
            Log.error(error, category: Constants.LogCategory.settings)
            guard appSettings == updated else { return }
            appSettings = persistedAppSettings
            alertItem = AlertItem(title: L10n.Settings.errorSaveFailed)
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
                endMinutes: endMinutes,
                defaultKey: mealTypes[index].defaultKey
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
