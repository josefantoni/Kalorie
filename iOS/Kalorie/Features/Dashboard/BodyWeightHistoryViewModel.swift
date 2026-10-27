//
//  BodyWeightHistoryViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

enum BodyWeightRange: CaseIterable {
    case week
    case month
    case year

    // MARK: - Properties

    var component: Calendar.Component {
        switch self {
        case .week: .weekOfYear
        case .month: .month
        case .year: .year
        }
    }
}

struct BodyWeightChartPoint: Equatable {
    let date: Date
    let kilograms: Double
}

final class BodyWeightHistoryViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published private(set) var weights: [BodyWeightDomain] = [] {
        didSet { refreshChart() }
    }
    @Published private(set) var periodOffset = 0 {
        didSet { refreshChart() }
    }
    @Published private(set) var period = DateInterval(start: .distantPast, duration: 0)
    @Published private(set) var chartPoints: [BodyWeightChartPoint] = []
    @Published var range = BodyWeightRange.month {
        didSet { periodOffset = 0 }
    }
    @Published var alertItem: AlertItem?
    @Published var isDeleteConfirmationVisible = false
    @Published var editingDayKey: String?
    @Published var editingKilograms = Constants.BodyWeight.defaultKilograms
    @Published private(set) var isSaving = false
    @Published private(set) var showCheckmark = false

    let unit: BodyWeightUnit

    private var pendingDeletion: BodyWeightDomain?
    private var hasChanges = false

    private let fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol
    private let saveBodyWeight: any SaveBodyWeightUseCaseProtocol
    private let deleteBodyWeight: any DeleteBodyWeightUseCaseProtocol
    private let now: () -> Date
    private let onChanged: () -> Void

    // MARK: - Init

    init(
        unit: BodyWeightUnit,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol,
        saveBodyWeight: any SaveBodyWeightUseCaseProtocol,
        deleteBodyWeight: any DeleteBodyWeightUseCaseProtocol,
        now: @escaping () -> Date = { Date.now },
        onChanged: @escaping () -> Void
    ) {
        self.unit = unit
        self.fetchBodyWeights = fetchBodyWeights
        self.saveBodyWeight = saveBodyWeight
        self.deleteBodyWeight = deleteBodyWeight
        self.now = now
        self.onChanged = onChanged
        refreshChart()
    }

    // MARK: - Functions

    var canPageForward: Bool { periodOffset < 0 }

    @MainActor
    func onAppear() async {
        do {
            weights = try await fetchBodyWeights()
            state = .loaded(())
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.WeightHistory.errorLoadFailed)
            state = .loaded(())
        }
    }

    func onDisappear() {
        guard hasChanges else { return }
        hasChanges = false
        onChanged()
    }

    func onPreviousPeriod() {
        periodOffset -= 1
    }

    func onNextPeriod() {
        guard canPageForward else { return }
        periodOffset += 1
    }

    func onEditRequested(_ weight: BodyWeightDomain) {
        editingKilograms = weight.kilograms
        editingDayKey = weight.dayKey
    }

    @MainActor
    func onEditConfirmed() async {
        guard
            !isSaving,
            !showCheckmark,
            let dayKey = editingDayKey,
            let day = BodyWeightDayKey.date(from: dayKey)
        else { return }
        isSaving = true
        do {
            try await saveBodyWeight(kilograms: editingKilograms, for: day)
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.Dashboard.errorWeightSaveFailed)
            isSaving = false
            return
        }
        isSaving = false
        hasChanges = true
        showCheckmark = true
        weights = weights.map {
            $0.dayKey == dayKey ? BodyWeightDomain(dayKey: dayKey, kilograms: editingKilograms) : $0
        }
        try? await Task.sleep(for: .seconds(1))
        editingDayKey = nil
        showCheckmark = false
    }

    func onDeleteRequested(_ weight: BodyWeightDomain) {
        pendingDeletion = weight
        isDeleteConfirmationVisible = true
    }

    @MainActor
    func onDeleteConfirmed() async {
        guard let weight = pendingDeletion else { return }
        pendingDeletion = nil
        do {
            try await deleteBodyWeight(dayKey: weight.dayKey)
            hasChanges = true
            weights.removeAll { $0.dayKey == weight.dayKey }
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            alertItem = AlertItem(title: L10n.WeightHistory.errorDeleteFailed)
        }
    }

    private func refreshChart() {
        let calendar = Calendar.current
        let anchor = calendar.date(byAdding: range.component, value: periodOffset, to: now()) ?? now()
        let interval = calendar.dateInterval(of: range.component, for: anchor) ?? DateInterval(start: anchor, duration: 0)
        period = interval
        chartPoints = weights
            .compactMap { weight -> BodyWeightChartPoint? in
                guard
                    let date = BodyWeightDayKey.date(from: weight.dayKey),
                    interval.contains(date)
                else { return nil }
                return BodyWeightChartPoint(date: date, kilograms: weight.kilograms)
            }
            .sorted { $0.date < $1.date }
    }
}
