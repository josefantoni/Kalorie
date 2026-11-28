//
//  DailyGoalsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import Foundation
import MacroKit

enum GoalMacro {
    case protein
    case carbs
    case fat
}

final class DailyGoalsViewModel: ObservableObject {

    // MARK: - Properties

    @Published var kcalText: String {
        didSet {
            syncSelectedVariant()
            if kcalText != oldValue {
                isKcalOutsideRangeAccepted = false
                if !isConvertingMacros {
                    hasPendingKcalRescale = true
                }
            }
            clearResolvedMacroModeRejection()
        }
    }
    @Published private(set) var macroMode: GoalMacroMode
    @Published var proteinText: String {
        didSet { macroTextDidChange() }
    }
    @Published var carbsText: String {
        didSet { macroTextDidChange() }
    }
    @Published var fatText: String {
        didSet { macroTextDidChange() }
    }
    @Published var sex: GoalSex? {
        willSet {
            guard newValue != sex else { return }
            syncSelectedVariant()
        }
        didSet {
            guard sex != oldValue else { return }
            updateEstimate()
            applyEstimateOrReferenceKcal()
        }
    }
    @Published var birthYear: Int? {
        didSet { updateEstimate() }
    }
    @Published var heightCm: Int? {
        didSet { updateEstimate() }
    }
    @Published var weightKilograms: Double? {
        didSet { updateEstimate() }
    }
    @Published var isWeightPickerVisible = false
    @Published var isBirthYearPickerVisible = false
    @Published var isHeightPickerVisible = false
    @Published var activityLevel: GoalActivityLevel? {
        willSet {
            guard newValue != activityLevel else { return }
            syncSelectedVariant()
        }
        didSet {
            guard activityLevel != oldValue else { return }
            updateEstimate()
            onVariantSelected(Int(selectedVariantIndex))
        }
    }
    @Published var selectedVariantIndex = Double(GoalEstimate.percentChanges.firstIndex(of: 0) ?? 0)
    @Published private(set) var isSaving = false
    @Published private(set) var isKcalOutsideRangeAccepted = false
    @Published private(set) var didFinish = false
    @Published var isSaveConfirmationVisible = false
    @Published var isEndConfirmationVisible = false
    @Published var alertItem: AlertItem?
    @Published private(set) var rejectedMacroMode: GoalMacroMode?
    private var rejectedMacroModeBlock: MacroModeSwitchBlock?
    @Published private(set) var kcalFocusRequestCount = 0
    @Published private(set) var isBodyDataCollapsed: Bool
    @Published private(set) var isActivityCollapsed: Bool

    let unit: BodyWeightUnit
    let hasActiveGoal: Bool
    let isBodyDataRemembered: Bool
    let isActivityRemembered: Bool

    private static let defaultPercentSplit = (protein: "20", carbs: "50", fat: "30")

    private(set) var estimate: GoalEstimate?
    private var gramsSplitAnchor: [Double]?
    private var initialInputs: Inputs?
    private var isConvertingMacros = false
    private var hasPendingKcalRescale = false
    private var isWeightDefaulted = false
    private let fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol
    private let saveDailyGoal: any SaveDailyGoalUseCaseProtocol
    private let saveBodyWeight: any SaveBodyWeightUseCaseProtocol
    private let onChanged: (DailyGoalHistory) -> Void
    private let now: () -> Date

    // MARK: - Init

    init(
        history: DailyGoalHistory,
        unit: BodyWeightUnit,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol,
        saveDailyGoal: any SaveDailyGoalUseCaseProtocol,
        saveBodyWeight: any SaveBodyWeightUseCaseProtocol,
        now: @escaping () -> Date = Date.init,
        onChanged: @escaping (DailyGoalHistory) -> Void
    ) {
        self.unit = unit
        self.fetchBodyWeights = fetchBodyWeights
        self.saveDailyGoal = saveDailyGoal
        self.saveBodyWeight = saveBodyWeight
        self.now = now
        self.onChanged = onChanged

        let dayKey = BodyWeightDayKey.make(from: now())
        let latest = history.latestEntry(onDayKey: dayKey)
        let profile = history.latestProfileEntry(onDayKey: dayKey) ?? latest
        let active = latest.flatMap { $0.isActive ? $0 : nil }
        hasActiveGoal = active != nil
        kcalText = active?.kcalGoal.map(Self.format) ?? ""
        macroMode = active?.macroMode ?? .percent
        if
            let active,
            active.macroMode == .grams
        {
            proteinText = active.proteinGrams.map(Self.format) ?? ""
            carbsText = active.carbsGrams.map(Self.format) ?? ""
            fatText = active.fatGrams.map(Self.format) ?? ""
        } else if let active {
            proteinText = active.proteinPercent.map(Self.format) ?? ""
            carbsText = active.carbsPercent.map(Self.format) ?? ""
            fatText = active.fatPercent.map(Self.format) ?? ""
        } else {
            proteinText = Self.defaultPercentSplit.protein
            carbsText = Self.defaultPercentSplit.carbs
            fatText = Self.defaultPercentSplit.fat
        }
        sex = profile?.sex ?? .male
        birthYear = profile?.birthYear
        heightCm = profile?.heightCm.map { Int($0.rounded()) }
        weightKilograms = profile?.weightKg
        activityLevel = profile?.activityLevel ?? .light
        isBodyDataRemembered = profile?.sex != nil
            && profile?.birthYear != nil
            && profile?.heightCm != nil
            && profile?.weightKg != nil
        isActivityRemembered = profile?.activityLevel != nil
        isBodyDataCollapsed = isBodyDataRemembered
        isActivityCollapsed = isActivityRemembered
        if active == nil {
            let reference = Self.referenceKcal(isMale: (profile?.sex ?? .male) == .male)
            kcalText = reference
        }
        updateEstimate()
        syncSelectedVariant()
        initialInputs = inputs
    }

    // MARK: - Types

    private struct Inputs: Equatable {
        var kcalText: String
        var macroMode: GoalMacroMode
        var proteinText: String
        var carbsText: String
        var fatText: String
        var sex: GoalSex?
        var birthYear: Int?
        var heightCm: Int?
        var weightKilograms: Double?
        var activityLevel: GoalActivityLevel?
    }

    // MARK: - Functions

    private var inputs: Inputs {
        Inputs(
            kcalText: kcalText,
            macroMode: macroMode,
            proteinText: proteinText,
            carbsText: carbsText,
            fatText: fatText,
            sex: sex,
            birthYear: birthYear,
            heightCm: heightCm,
            weightKilograms: weightKilograms,
            activityLevel: activityLevel
        )
    }

    private var inputsAfterPendingRescale: Inputs {
        var result = inputs
        guard
            hasPendingKcalRescale,
            macroMode == .grams,
            let kcal,
            let shares = gramsSplitAnchor ?? currentGramsShares()
        else { return result }
        (result.proteinText, result.carbsText, result.fatText) = gramsTexts(kcal: kcal, shares: shares)
        return result
    }

    var kcal: Double? { Self.parse(kcalText) }

    var protein: Double? { Self.parse(proteinText) }

    var carbs: Double? { Self.parse(carbsText) }

    var fat: Double? { Self.parse(fatText) }

    var currentYear: Int { BodyWeightDayKey.year(of: now()) }

    var birthYears: [Int] {
        Array((currentYear - GoalLimits.maxAge)...(currentYear - GoalLimits.minAge)).reversed()
    }

    var percentSum: Double? {
        guard
            let protein,
            let carbs,
            let fat
        else { return nil }
        return protein + carbs + fat
    }

    var isPercentSumInvalid: Bool {
        guard macroMode == .percent else { return false }
        guard let sum = percentSum else { return true }
        return abs(sum - 100) > 0.001
    }

    var gramsInPercentMode: GoalMacroGrams? {
        guard
            macroMode == .percent,
            let kcal,
            let protein,
            let carbs,
            let fat
        else { return nil }
        var goal = DailyGoalDomain(validFrom: "", kcalGoal: kcal, macroMode: .percent)
        goal.proteinPercent = protein
        goal.carbsPercent = carbs
        goal.fatPercent = fat
        return goal.macroGrams
    }

    var kcalOfEnteredGrams: Double? {
        guard
            macroMode == .grams,
            let protein,
            let carbs,
            let fat
        else { return nil }
        return GoalMacroGrams(protein: protein, carbs: carbs, fat: fat).kcal
    }

    var kcalOfEnteredGramsDifferingFromGoal: Double? {
        guard
            let kcalOfEnteredGrams,
            let kcal,
            GoalsKt.gramsKcalDiffersFromGoal(gramsKcal: kcalOfEnteredGrams, goalKcal: kcal)
        else { return nil }
        return kcalOfEnteredGrams
    }

    private func updateEstimate() {
        let hadEstimate = estimate != nil
        let previousRange = kcalRangeOfVariants
        estimate = GoalEstimate(
            sex: sex,
            birthYear: birthYear,
            heightCm: heightCm.map(Double.init),
            weightKg: weightKilograms,
            activityLevel: activityLevel,
            currentYear: currentYear
        )
        if kcalRangeOfVariants != previousRange {
            isKcalOutsideRangeAccepted = false
        }
        if
            !hadEstimate,
            estimate != nil,
            !hasActiveGoal,
            isKcalUntouchedPrefill
        {
            onVariantSelected(Int(selectedVariantIndex))
        }
    }

    private var isKcalUntouchedPrefill: Bool {
        guard !kcalText.isEmpty else { return true }
        guard let sex else { return false }
        return kcalText == Self.referenceKcal(isMale: sex == .male)
    }

    var matchingVariantIndex: Int? {
        estimate?.variants.firstIndex { Double($0.kcal) == kcal }
    }

    var selectedVariant: GoalVariant? {
        matchingVariantIndex.flatMap { estimate?.variants[$0] }
    }

    var variantSliderPosition: Double {
        guard
            let kcals = estimate?.variants.map({ Double($0.kcal) }),
            let kcal
        else { return selectedVariantIndex }
        if let matchingVariantIndex {
            return Double(matchingVariantIndex)
        }
        guard let upper = kcals.firstIndex(where: { $0 > kcal }) else { return Double(kcals.count - 1) }
        guard upper > 0 else { return 0 }
        return Double(upper - 1) + (kcal - kcals[upper - 1]) / (kcals[upper] - kcals[upper - 1])
    }

    var kcalRangeOfVariants: ClosedRange<Int>? {
        guard
            let lower = estimate?.variants.first?.kcal,
            let upper = estimate?.variants.last?.kcal
        else { return nil }
        return lower...upper
    }

    var kcalRangeExceededByGoal: ClosedRange<Int>? {
        guard
            let range = kcalRangeOfVariants,
            let kcal,
            SaveDailyGoalUseCase.kcalRange.contains(kcal),
            kcal < Double(range.lowerBound) || kcal > Double(range.upperBound)
        else { return nil }
        return range
    }

    private func syncSelectedVariant() {
        guard
            estimate != nil,
            kcal != nil
        else { return }
        selectedVariantIndex = variantSliderPosition.rounded()
    }

    var kcalPlaceholder: String {
        switch sex {
        case .male: Self.referenceKcal(isMale: true)
        case .female: Self.referenceKcal(isMale: false)
        case nil: "\(Self.referenceKcal(isMale: false))–\(Self.referenceKcal(isMale: true))"
        }
    }

    private func macroTextDidChange() {
        if !isConvertingMacros {
            gramsSplitAnchor = nil
        }
        syncKcalWithGrams()
        clearResolvedMacroModeRejection()
    }

    private func clearResolvedMacroModeRejection() {
        guard
            let rejectedMacroMode,
            macroModeSwitchBlock(to: rejectedMacroMode) != rejectedMacroModeBlock
        else { return }
        self.rejectedMacroMode = nil
        rejectedMacroModeBlock = nil
    }

    private func syncKcalWithGrams() {
        guard
            macroMode == .grams,
            !isConvertingMacros,
            let kcalOfEnteredGrams
        else { return }
        isConvertingMacros = true
        defer { isConvertingMacros = false }
        kcalText = Self.format(kcalOfEnteredGrams.rounded())
    }

    func onKcalEditingEnded() {
        guard hasPendingKcalRescale else { return }
        hasPendingKcalRescale = false
        guard
            macroMode == .grams,
            let kcal
        else { return }
        rescaleGrams(toKcal: kcal)
    }

    private func currentGramsShares() -> [Double]? {
        guard
            let protein,
            let carbs,
            let fat
        else { return nil }
        let shares = EnergyKt.energyShares(protein: protein, carbohydrate: carbs, fat: fat).map { $0.doubleValue * 100 }
        return shares.reduce(0, +) > 0 ? shares : nil
    }

    private func rescaleGrams(toKcal kcal: Double) {
        guard let shares = gramsSplitAnchor ?? currentGramsShares() else { return }
        gramsSplitAnchor = shares
        isConvertingMacros = true
        defer { isConvertingMacros = false }
        (proteinText, carbsText, fatText) = gramsTexts(kcal: kcal, shares: shares)
    }

    private func gramsTexts(kcal: Double, shares: [Double]) -> (protein: String, carbs: String, fat: String) {
        (
            Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[0], isFat: false).rounded()),
            Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[1], isFat: false).rounded()),
            Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[2], isFat: true).rounded())
        )
    }

    private func rebalanceInvalidPercentSplit() {
        guard isPercentSumInvalid else { return }
        let entered = [protein, carbs, fat].compactMap { $0 }.filter { $0.isFinite && $0 >= 0 }
        let sum = entered.reduce(0, +)
        guard
            entered.count == 3,
            sum > 0
        else {
            proteinText = Self.defaultPercentSplit.protein
            carbsText = Self.defaultPercentSplit.carbs
            fatText = Self.defaultPercentSplit.fat
            return
        }
        applyRoundedPercentSplit(entered.map { $0 / sum * 100 })
    }

    private func applyRoundedPercentSplit(_ shares: [Double]) {
        let rounded = GoalsKt.roundedToHundred(values: shares.map { KotlinDouble(value: $0) }).map(\.doubleValue)
        proteinText = Self.format(rounded[0])
        carbsText = Self.format(rounded[1])
        fatText = Self.format(rounded[2])
    }

    private func applyEstimateOrReferenceKcal() {
        if estimate != nil {
            onVariantSelected(Int(selectedVariantIndex))
        } else {
            applyReferenceKcal()
        }
    }

    private func applyReferenceKcal() {
        guard let sex else { return }
        kcalText = Self.referenceKcal(isMale: sex == .male)
        onKcalEditingEnded()
    }

    var canSave: Bool {
        let macroRange = macroMode == .percent ? SaveDailyGoalUseCase.macroPercentRange : SaveDailyGoalUseCase.macroGramsRange
        let pending = inputsAfterPendingRescale
        guard
            !isSaving,
            let kcal,
            SaveDailyGoalUseCase.kcalRange.contains(kcal),
            let protein = Self.parse(pending.proteinText),
            let carbs = Self.parse(pending.carbsText),
            let fat = Self.parse(pending.fatText),
            macroRange.contains(protein),
            macroRange.contains(carbs),
            macroRange.contains(fat)
        else { return false }
        let isKcalAllowed = kcalRangeExceededByGoal == nil || isKcalOutsideRangeAccepted
        return !isPercentSumInvalid && isKcalAllowed && pending != initialInputs
    }

    func heightLabel(cm: Int) -> String {
        switch unit {
        case .kg:
            return "\(cm) cm"
        case .lb:
            let totalInches = Int((Double(cm) / 2.54).rounded())
            return "\(totalInches / 12)′ \(totalInches % 12)″"
        }
    }

    @MainActor
    func onAppear() async {
        guard weightKilograms == nil || isWeightDefaulted else { return }
        do {
            guard let latest = try await fetchBodyWeights(upTo: now()).first else { return }
            if weightKilograms == nil || isWeightDefaulted {
                let wasUntouched = inputs == initialInputs
                weightKilograms = latest.kilograms
                if wasUntouched {
                    initialInputs = inputs
                } else {
                    initialInputs?.weightKilograms = latest.kilograms
                }
                isWeightDefaulted = false
            }
        } catch {
            Log.warning(error, category: Constants.LogCategory.dailyGoals)
        }
    }

    var macroModeHint: String? {
        guard
            let rejectedMacroMode,
            let block = macroModeSwitchBlock(to: rejectedMacroMode)
        else { return nil }
        switch block {
        case .incompleteMacros:
            return L10n.Goals.hintMacroModeIncomplete
        case .missingKcal:
            return L10n.Goals.hintMacroModeNeedsKcal
        }
    }

    func onMacroModeChanged(_ mode: GoalMacroMode) {
        guard mode != macroMode else { return }
        onKcalEditingEnded()
        if let block = macroModeSwitchBlock(to: mode) {
            rejectedMacroMode = mode
            rejectedMacroModeBlock = block
            if block == .missingKcal {
                kcalFocusRequestCount += 1
            }
            return
        }
        rejectedMacroMode = nil
        rejectedMacroModeBlock = nil
        gramsSplitAnchor = nil
        let isKcalFollowingGrams = mode == .grams && isPercentSumInvalid
        isConvertingMacros = true
        defer {
            macroMode = mode
            isConvertingMacros = false
        }
        guard
            let protein,
            let carbs,
            let fat
        else { return }
        switch mode {
        case .grams:
            guard let kcal else { return }
            proteinText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: protein, isFat: false).rounded())
            carbsText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: carbs, isFat: false).rounded())
            fatText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: fat, isFat: true).rounded())
            if isKcalFollowingGrams {
                followKcalOfConvertedGrams()
            }
        case .percent:
            let shares = EnergyKt.energyShares(protein: protein, carbohydrate: carbs, fat: fat).map { $0.doubleValue * 100 }
            guard shares.reduce(0, +) > 0 else { return }
            applyRoundedPercentSplit(shares)
        }
    }

    private func followKcalOfConvertedGrams() {
        guard
            let protein,
            let carbs,
            let fat
        else { return }
        let gramsKcal = GoalMacroGrams(protein: protein, carbs: carbs, fat: fat).kcal.rounded()
        guard gramsKcal > 0 else { return }
        kcalText = Self.format(gramsKcal)
    }

    func onStep(_ macro: GoalMacro, direction: Int) {
        onKcalEditingEnded()
        let step = macroMode == .percent ? 1.0 : 5.0
        let upper = macroMode == .percent ? 100.0 : 1000.0
        func stepped(_ text: String) -> String {
            let value = Self.parse(text) ?? 0
            return Self.format(min(max(value + step * Double(direction), 0), upper))
        }
        switch macro {
        case .protein: proteinText = stepped(proteinText)
        case .carbs: carbsText = stepped(carbsText)
        case .fat: fatText = stepped(fatText)
        }
    }

    func onBirthYearTapped() {
        if birthYear == nil {
            birthYear = currentYear - 30
        }
        isBirthYearPickerVisible = true
    }

    func onHeightTapped() {
        if heightCm == nil {
            heightCm = sex == .female ? 165 : 178
        }
        isHeightPickerVisible = true
    }

    func onWeightTapped() {
        if weightKilograms == nil {
            weightKilograms = Constants.BodyWeight.defaultKilograms
            isWeightDefaulted = true
        }
        isWeightPickerVisible = true
    }

    func onWeightChanged(_ kilograms: Double) {
        weightKilograms = kilograms
        isWeightDefaulted = false
    }

    func onBodyDataToggled() {
        isBodyDataCollapsed.toggle()
    }

    func onActivityToggled() {
        isActivityCollapsed.toggle()
    }

    func onKcalOutsideRangeAcceptanceToggled() {
        isKcalOutsideRangeAccepted.toggle()
    }

    func onVariantSliderReleased() {
        guard matchingVariantIndex == nil else { return }
        onVariantSelected(Int(variantSliderPosition.rounded()))
    }

    func onVariantStep(direction: Int) {
        guard let kcals = estimate?.variants.map({ Double($0.kcal) }) else { return }
        guard let kcal else {
            onVariantSliderMoved(Int(selectedVariantIndex) + direction)
            return
        }
        let index = direction > 0 ? kcals.firstIndex { $0 > kcal } : kcals.lastIndex { $0 < kcal }
        guard let index else { return }
        onVariantSliderMoved(index)
    }

    func onVariantSliderMoved(_ index: Int) {
        onVariantSelected(index)
        rebalanceInvalidPercentSplit()
    }

    func onVariantSelected(_ index: Int) {
        guard
            let variants = estimate?.variants,
            variants.indices.contains(index)
        else { return }
        selectedVariantIndex = Double(index)
        kcalText = Self.format(Double(variants[index].kcal))
        onKcalEditingEnded()
    }

    @MainActor
    func onSaveTapped() async {
        onKcalEditingEnded()
        guard
            canSave,
            let kcal,
            let protein,
            let carbs,
            let fat
        else { return }
        var goal = DailyGoalDomain(
            validFrom: "",
            kcalGoal: kcal,
            macroMode: macroMode,
            sex: sex,
            birthYear: birthYear,
            heightCm: heightCm.map(Double.init),
            weightKg: weightKilograms,
            activityLevel: activityLevel
        )
        switch macroMode {
        case .percent:
            goal.proteinPercent = protein
            goal.carbsPercent = carbs
            goal.fatPercent = fat
        case .grams:
            goal.proteinGrams = protein
            goal.carbsGrams = carbs
            goal.fatGrams = fat
        }
        let isWeightEdited = !isWeightDefaulted
            && goal.weightKg.map(unit.tenths(kilograms:)) != initialInputs?.weightKilograms.map(unit.tenths(kilograms:))
        await save(goal, loggingWeight: isWeightEdited ? goal.weightKg : nil)
    }

    @MainActor
    func onEndConfirmed() async {
        guard !isSaving else { return }
        await save(.ended(validFrom: ""), loggingWeight: nil)
    }

    // MARK: - Private

    @MainActor
    private func save(_ goal: DailyGoalDomain, loggingWeight kilograms: Double?) async {
        isSaving = true
        defer { isSaving = false }
        do {
            let history = try await saveDailyGoal(goal)
            if let kilograms {
                await logWeightIfMissing(kilograms)
            }
            onChanged(history)
            didFinish = true
        } catch {
            guard !Task.isCancelled else { return }
            Log.error(error, category: Constants.LogCategory.dailyGoals)
            alertItem = AlertItem(title: L10n.Goals.errorSaveFailed)
        }
    }

    @MainActor
    private func logWeightIfMissing(_ kilograms: Double) async {
        do {
            let today = now()
            let latest = try await fetchBodyWeights(upTo: today).first
            guard latest?.dayKey != BodyWeightDayKey.make(from: today) else { return }
            try await saveBodyWeight(kilograms: kilograms, for: today)
        } catch {
            Log.warning(error, category: Constants.LogCategory.dailyGoals)
        }
    }

    private enum MacroModeSwitchBlock {
        case incompleteMacros
        case missingKcal
    }

    private func macroModeSwitchBlock(to mode: GoalMacroMode) -> MacroModeSwitchBlock? {
        let filledMacros = [protein, carbs, fat].compactMap { $0 }.filter { $0.isFinite && $0 >= 0 }.count
        if
            filledMacros != 0,
            filledMacros != 3
        {
            return .incompleteMacros
        }
        if
            mode == .grams,
            kcal == nil,
            filledMacros == 3
        {
            return .missingKcal
        }
        return nil
    }

    private static func referenceKcal(isMale: Bool) -> String {
        Int(GoalsKt.referenceKcal(isMale: isMale)).formatted(.number.grouping(.never))
    }

    private static func parse(_ text: String) -> Double? {
        Double(text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: "."))
    }

    private static func format(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...6)).grouping(.never))
    }
}
