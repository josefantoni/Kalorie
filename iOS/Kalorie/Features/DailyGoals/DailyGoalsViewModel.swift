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
            if let index = matchingVariantIndex {
                selectedVariantIndex = Double(index)
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
            guard
                newValue != sex,
                let index = matchingVariantIndex
            else { return }
            selectedVariantIndex = Double(index)
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
            guard
                newValue != activityLevel,
                let index = matchingVariantIndex
            else { return }
            selectedVariantIndex = Double(index)
        }
        didSet {
            guard activityLevel != oldValue else { return }
            updateEstimate()
            onVariantSelected(Int(selectedVariantIndex))
        }
    }
    @Published var selectedVariantIndex = Double(GoalEstimate.percentChanges.firstIndex(of: 0) ?? 0)
    @Published private(set) var isSaving = false
    @Published private(set) var didFinish = false
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

    private(set) var estimate: GoalEstimate?
    private var gramsSplitAnchor: [Double]?
    private var initialInputs: Inputs?
    private var isConvertingMacros = false
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
            proteinText = "20"
            carbsText = "50"
            fatText = "30"
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
        if let index = matchingVariantIndex {
            selectedVariantIndex = Double(index)
        }
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
        estimate = GoalEstimate(
            sex: sex,
            birthYear: birthYear,
            heightCm: heightCm.map(Double.init),
            weightKg: weightKilograms,
            activityLevel: activityLevel,
            currentYear: currentYear
        )
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
        kcalText = Self.format(kcalOfEnteredGrams.rounded())
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
        proteinText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[0], isFat: false).rounded())
        carbsText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[1], isFat: false).rounded())
        fatText = Self.format(EnergyKt.gramsFromKcal(kcal: kcal, percent: shares[2], isFat: true).rounded())
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
        let reference = Self.referenceKcal(isMale: sex == .male)
        if
            macroMode == .grams,
            let referenceKcal = Self.parse(reference)
        {
            rescaleGrams(toKcal: referenceKcal)
        }
        kcalText = reference
    }

    var canSave: Bool {
        let macroRange = macroMode == .percent ? SaveDailyGoalUseCase.macroPercentRange : SaveDailyGoalUseCase.macroGramsRange
        guard
            !isSaving,
            let kcal,
            SaveDailyGoalUseCase.kcalRange.contains(kcal),
            let protein,
            let carbs,
            let fat,
            macroRange.contains(protein),
            macroRange.contains(carbs),
            macroRange.contains(fat)
        else { return false }
        return !isPercentSumInvalid && inputs != initialInputs
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
        case .percentSumNot100:
            return L10n.Goals.hintMacroModeSumNot100
        }
    }

    func onMacroModeChanged(_ mode: GoalMacroMode) {
        guard mode != macroMode else { return }
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
        case .percent:
            let shares = EnergyKt.energyShares(protein: protein, carbohydrate: carbs, fat: fat).map { $0.doubleValue * 100 }
            guard shares.reduce(0, +) > 0 else { return }
            let rounded = GoalsKt.roundedToHundred(values: shares.map { KotlinDouble(value: $0) }).map(\.doubleValue)
            proteinText = Self.format(rounded[0])
            carbsText = Self.format(rounded[1])
            fatText = Self.format(rounded[2])
        }
    }

    func onStep(_ macro: GoalMacro, direction: Int) {
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

    func onVariantSliderReleased() {
        guard matchingVariantIndex == nil else { return }
        onVariantSelected(Int(selectedVariantIndex))
    }

    func onVariantSelected(_ index: Int) {
        guard
            let variants = estimate?.variants,
            variants.indices.contains(index)
        else { return }
        selectedVariantIndex = Double(index)
        let variantKcal = Double(variants[index].kcal)
        if macroMode == .grams {
            rescaleGrams(toKcal: variantKcal)
        }
        kcalText = Self.format(variantKcal)
    }

    @MainActor
    func onSaveTapped() async {
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
        case percentSumNot100
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
        if
            mode == .grams,
            macroMode == .percent,
            filledMacros == 3,
            isPercentSumInvalid
        {
            return .percentSumNot100
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
