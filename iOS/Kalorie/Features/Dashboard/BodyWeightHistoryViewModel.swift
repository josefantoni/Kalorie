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
        didSet { periodOffset = offsetOfSelectedDay(in: range) }
    }
    @Published private(set) var isLoadingEarlierYear = false
    @Published var alertItem: AlertItem?
    @Published var isDeleteConfirmationVisible = false

    let unit: BodyWeightUnit

    private var pendingDeletion: BodyWeightDomain?
    private var hasChanges = false
    private var oldestLoadedYear: Int?

    private let fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol
    private let deleteBodyWeight: any DeleteBodyWeightUseCaseProtocol
    private let selectedDay: Date
    private let now: () -> Date
    private let onChanged: () -> Void

    // MARK: - Init

    init(
        unit: BodyWeightUnit,
        selectedDay: Date,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol,
        deleteBodyWeight: any DeleteBodyWeightUseCaseProtocol,
        now: @escaping () -> Date = { Date.now },
        onChanged: @escaping () -> Void
    ) {
        self.unit = unit
        self.selectedDay = selectedDay
        self.fetchBodyWeights = fetchBodyWeights
        self.deleteBodyWeight = deleteBodyWeight
        self.now = now
        self.onChanged = onChanged
        periodOffset = offsetOfSelectedDay(in: range)
        refreshChart()
    }

    // MARK: - Functions

    var canPageForward: Bool { periodOffset < 0 }

    var canPageBack: Bool {
        BodyWeightDayKey.make(from: period.start) > String(format: "%04d-01-01", Constants.BodyWeight.firstYear)
    }

    @MainActor
    func onAppear() async {
        let currentYear = BodyWeightDayKey.year(of: now())
        do {
            weights = try await fetchBodyWeights(inYear: currentYear)
            oldestLoadedYear = currentYear
            state = .loaded(())
        } catch {
            Log.error(error, category: Constants.LogCategory.dashboard)
            state = .error(error)
        }
        await loadEarlierYearsIfNeeded()
    }

    @MainActor
    func onRetryTapped() async {
        state = .loading
        await onAppear()
    }

    @MainActor
    func loadEarlierYearsIfNeeded() async {
        guard !isLoadingEarlierYear else { return }
        isLoadingEarlierYear = true
        defer { isLoadingEarlierYear = false }
        while
            let oldest = oldestLoadedYear,
            oldest > max(BodyWeightDayKey.year(of: period.start), Constants.BodyWeight.firstYear)
        {
            do {
                let earlier = try await fetchBodyWeights(inYear: oldest - 1)
                weights = (weights + earlier).sorted { $0.dayKey > $1.dayKey }
                oldestLoadedYear = oldest - 1
            } catch {
                Log.error(error, category: Constants.LogCategory.dashboard)
                alertItem = AlertItem(title: L10n.WeightHistory.errorLoadFailed)
                return
            }
        }
    }

    func onDisappear() {
        guard hasChanges else { return }
        hasChanges = false
        onChanged()
    }

    @MainActor
    func onPreviousPeriod() async {
        guard canPageBack else { return }
        periodOffset -= 1
        await loadEarlierYearsIfNeeded()
    }

    func onNextPeriod() {
        guard canPageForward else { return }
        periodOffset += 1
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

    private func offsetOfSelectedDay(in range: BodyWeightRange) -> Int {
        let calendar = Calendar.current
        guard
            let current = calendar.dateInterval(of: range.component, for: now()),
            let selected = calendar.dateInterval(of: range.component, for: selectedDay),
            let offset = calendar.dateComponents([range.component], from: current.start, to: selected.start).value(for: range.component)
        else { return 0 }
        return min(offset, 0)
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
