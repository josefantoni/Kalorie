//
//  DailyGoalsView.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import SwiftUI

struct DailyGoalsView: View {

    // MARK: - Properties

    @StateObject private var viewModel: DailyGoalsViewModel
    @Environment(\.dismiss) private var dismiss
    @FocusState private var isKcalFocused: Bool

    // MARK: - Init

    init(viewModel: @autoclosure @escaping () -> DailyGoalsViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel())
    }

    // MARK: - Body

    var body: some View {
        ScrollViewReader { proxy in
            list
                .onChange(of: viewModel.kcalFocusRequestCount) {
                    isKcalFocused = true
                    Task {
                        try? await Task.sleep(for: .milliseconds(350))
                        withAnimation {
                            proxy.scrollTo(Self.kcalRowID, anchor: .center)
                        }
                    }
                }
        }
    }

    private static let kcalRowID = "kcalRow"

    private var list: some View {
        List {
            bodyDataSection
            activitySection
            currentGoalSection
            saveGoalSection
            if viewModel.hasActiveGoal {
                endGoalSection
            }
        }
        .environment(\.defaultMinListRowHeight, 56)
        .navigationTitle(L10n.Goals.navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(L10n.Common.buttonDone) {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            }
        }
        .task { await viewModel.onAppear() }
        .onChange(of: viewModel.didFinish) { _, didFinish in
            if didFinish { dismiss() }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(title: Text(item.title), message: item.message.map(Text.init), dismissButton: .default(Text(L10n.Common.ok)))
        }
        .alert(L10n.Goals.endTitle, isPresented: $viewModel.isEndConfirmationVisible) {
            Button(L10n.Goals.buttonEnd, role: .destructive) {
                Task { await viewModel.onEndConfirmed() }
            }
            Button(L10n.Common.buttonCancel, role: .cancel) {}
        } message: {
            Text(L10n.Goals.endMessage)
        }
    }

    // MARK: - Sections

    private var currentGoalSection: some View {
        Section(
            header: Text(L10n.Goals.sectionCurrent)
        ) {
            infoRow(L10n.Goals.footerCurrent)
            if let estimate = viewModel.estimate {
                VStack(alignment: .leading, spacing: 12){
                    valueRow(title: L10n.Goals.estimateExpenditure, value: kcalString(estimate.expenditure))
                    valueRow(title: L10n.Goals.estimateBmr, value: kcalString(estimate.basalRate))
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.accentColor)
                            .frame(width: 8, height: 8)
                        Text(L10n.Goals.estimateDisclaimer)
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }
                }
                VStack(alignment: .leading, spacing: 10){
                    variantSlider(estimate.variants)
                    kcalRow
                }
                if viewModel.selectedVariant?.isRaisedToBasalRate == true {
                    Text(L10n.Goals.variantRaised)
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
            } else {
                Text(L10n.Goals.estimateHint)
                    .font(.footnote)
                    .foregroundStyle(Color.secondary)
                kcalRow
            }
            VStack(spacing: 24){
                Picker(
                    L10n.Goals.sectionCurrent,
                    selection: Binding(
                        get: { viewModel.macroMode },
                        set: { viewModel.onMacroModeChanged($0) }
                    )
                ) {
                    Text(L10n.Goals.macroModePercent).tag(GoalMacroMode.percent)
                    Text(L10n.Goals.macroModeGrams).tag(GoalMacroMode.grams)
                }
                .pickerStyle(.segmented)
                macroRow(title: L10n.FoodQuantity.protein, macro: .protein, text: $viewModel.proteinText, grams: viewModel.gramsInPercentMode?.protein)
                macroRow(title: L10n.FoodQuantity.carbs, macro: .carbs, text: $viewModel.carbsText, grams: viewModel.gramsInPercentMode?.carbs)
                macroRow(title: L10n.FoodQuantity.fat, macro: .fat, text: $viewModel.fatText, grams: viewModel.gramsInPercentMode?.fat)
                if viewModel.isPercentSumInvalid {
                    Text(L10n.Goals.errorPercentSum)
                        .font(.caption)
                        .foregroundStyle(Color.error)
                }
                if let kcal = viewModel.kcalOfEnteredGramsDifferingFromGoal {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.accentColor)
                            .frame(width: 8, height: 8)
                        Text(L10n.Goals.gramsKcal(Int(kcal.rounded()).formatted()))
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }
                }
            }
        }
    }

    private var bodyDataSection: some View {
        Section {
            if viewModel.isBodyDataCollapsed {
                summaryRow(bodyDataSummary, action: viewModel.onBodyDataToggled)
            } else {
                bodyDataRows
            }
        } header: {
            collapsibleHeader(
                L10n.Goals.sectionBody,
                isCollapsible: viewModel.isBodyDataRemembered,
                isCollapsed: viewModel.isBodyDataCollapsed,
                action: viewModel.onBodyDataToggled
            )
        }
    }

    private var bodyDataRows: some View {
        Group {
            VStack(spacing: 12) {
                infoRow(L10n.Goals.footerBody)
                Picker(L10n.Goals.fieldSex, selection: $viewModel.sex) {
                    Text(L10n.Goals.sexMale).tag(GoalSex?.some(.male))
                    Text(L10n.Goals.sexFemale).tag(GoalSex?.some(.female))
                }
                .pickerStyle(.segmented)
            }
            wheelRow(
                title: L10n.Goals.fieldBirthYear,
                value: viewModel.birthYear.map(String.init),
                isPresented: $viewModel.isBirthYearPickerVisible,
                selection: Binding(
                    get: { viewModel.birthYear ?? viewModel.currentYear - 30 },
                    set: { viewModel.birthYear = $0 }
                ),
                wheel: (options: viewModel.birthYears, label: { String($0) }),
                onTap: viewModel.onBirthYearTapped
            )
            wheelRow(
                title: L10n.Goals.fieldHeight,
                value: viewModel.heightCm.map { viewModel.heightLabel(cm: $0) },
                isPresented: $viewModel.isHeightPickerVisible,
                selection: Binding(
                    get: { viewModel.heightCm ?? 178 },
                    set: { viewModel.heightCm = $0 }
                ),
                wheel: (options: Array(GoalLimits.heightRangeCm), label: { viewModel.heightLabel(cm: $0) }),
                onTap: viewModel.onHeightTapped
            )
            Button {
                viewModel.onWeightTapped()
            } label: {
                HStack {
                    Text(L10n.Goals.fieldWeight)
                        .foregroundStyle(Color.primary)
                    Spacer()
                    Text(viewModel.weightKilograms.map { viewModel.unit.formatted(kilograms: $0) } ?? "—")
                        .foregroundStyle(Color.secondary)
                    Image(systemName: BaseImageName.chevronUpDown.rawValue)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.secondary)
                }
            }
            .popover(isPresented: $viewModel.isWeightPickerVisible) {
                WeightPickerView(
                    kilograms: Binding(
                        get: { viewModel.weightKilograms ?? Constants.BodyWeight.defaultKilograms },
                        set: { viewModel.onWeightChanged($0) }
                    ),
                    unit: viewModel.unit
                )
                .padding()
                .frame(width: 280)
                .dynamicTypeLimit()
                .presentationCompactAdaptation(.popover)
            }
        }
    }

    private var activitySection: some View {
        Section {
            if viewModel.isActivityCollapsed {
                summaryRow(viewModel.activityLevel?.title ?? "—", action: viewModel.onActivityToggled)
            } else {
                activityRows
            }
        } header: {
            collapsibleHeader(
                L10n.Goals.sectionActivity,
                isCollapsible: viewModel.isActivityRemembered,
                isCollapsed: viewModel.isActivityCollapsed,
                action: viewModel.onActivityToggled
            )
        }
    }

    private var activityLevels: [GoalActivityLevel] { GoalActivityLevel.allCases }

    private var activityRows: some View {
        VStack(spacing: 4) {
            HStack {
                Text(L10n.Goals.activityScaleLow)
                Spacer()
                Text(L10n.Goals.activityScaleHigh)
            }
            .font(.caption)
            .foregroundStyle(Color.secondary)
            Slider(
                value: Binding(
                    get: { Double(activityLevels.firstIndex(of: viewModel.activityLevel ?? .light) ?? 0) },
                    set: { value in
                        let index = Int(value.rounded())
                        if activityLevels.indices.contains(index) {
                            viewModel.activityLevel = activityLevels[index]
                        }
                    }
                ),
                in: 0...Double(activityLevels.count - 1),
                step: 1
            )
            .accessibilityValue(viewModel.activityLevel?.title ?? "")
            activityDetail
        }
    }

    @ViewBuilder private var activityDetail: some View {
        if let level = viewModel.activityLevel {
            VStack(alignment: .leading, spacing: 2) {
                Text(level.title)
                    .font(.headline)
                Text(level.detail)
                    .font(.footnote)
                    .foregroundStyle(Color.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 4)
        }
    }

    private var bodyDataSummary: String {
        [
            viewModel.sex.map { $0 == .male ? L10n.Goals.sexMale : L10n.Goals.sexFemale },
            viewModel.birthYear.map(String.init),
            viewModel.heightCm.map { viewModel.heightLabel(cm: $0) },
            viewModel.weightKilograms.map { viewModel.unit.formatted(kilograms: $0) }
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }

    private func summaryRow(_ text: String, action: @escaping () -> Void) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.25)) {
                action()
            }
        } label: {
            Text(text)
                .foregroundStyle(Color.secondary)
                .font(.footnote)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func collapsibleHeader(
        _ title: String,
        isCollapsible: Bool,
        isCollapsed: Bool,
        action: @escaping () -> Void
    ) -> some View {
        HStack {
            Text(title)
            Spacer()
            if isCollapsible {
                BadgeButton(
                    imageName: .chevronDown,
                    size: 28,
                    iconRotation: .degrees(isCollapsed ? 90 : 0)
                ) {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        action()
                    }
                }
            }
        }
    }

    private func variantSlider(_ variants: [GoalVariant]) -> some View {
        VStack(spacing: 4) {
            SliderLabelsLayout {
                ForEach(Array(variants.enumerated()), id: \.element.id) { index, variant in
                    Text(variant.shortTitle)
                        .font(.caption)
                        .foregroundStyle(viewModel.matchingVariantIndex == index ? Color.primary : Color.secondary)
                }
            }
            Slider(
                value: Binding(
                    get: { Double(viewModel.matchingVariantIndex ?? Int(viewModel.selectedVariantIndex)) },
                    set: { viewModel.onVariantSelected(Int($0.rounded())) }
                ),
                in: 0...Double(variants.count - 1),
                step: 1
            ) { editing in
                if !editing {
                    viewModel.onVariantSliderReleased()
                }
            }
        }
    }

    private var saveGoalSection: some View {
        Section {
            PrimaryButton(L10n.Goals.buttonSave, style: .primary) {
                Task { await viewModel.onSaveTapped() }
            }
            .disabled(!viewModel.canSave)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
    }

    private var endGoalSection: some View {
        Section {
            PrimaryButton(L10n.Goals.buttonEnd, style: .destructive) {
                viewModel.isEndConfirmationVisible = true
            }
            .disabled(viewModel.isSaving)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
    }

    // MARK: - Functions

    private func wheelRow(
        title: String,
        value: String?,
        isPresented: Binding<Bool>,
        selection: Binding<Int>,
        wheel: (options: [Int], label: (Int) -> String),
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            HStack {
                Text(title)
                    .foregroundStyle(Color.primary)
                Spacer()
                Text(value ?? "—")
                    .foregroundStyle(Color.secondary)
                Image(systemName: BaseImageName.chevronUpDown.rawValue)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.secondary)
            }
        }
        .popover(isPresented: isPresented) {
            Picker(title, selection: selection) {
                ForEach(wheel.options, id: \.self) { option in
                    Text(wheel.label(option)).tag(option)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: 200, height: 216)
            .padding()
            .dynamicTypeLimit()
            .presentationCompactAdaptation(.popover)
        }
    }

    private func kcalString(_ kcal: Int) -> String {
        "\(kcal.formatted()) \(L10n.Common.unitKcal)"
    }

    private var kcalRow: some View {
        VStack(alignment: .leading, spacing: 4) {
            numberRow(title: L10n.Goals.fieldNewKcal, placeholder: viewModel.kcalPlaceholder, text: $viewModel.kcalText)
            if let hint = viewModel.macroModeHint {
                Text(hint)
                    .font(.caption)
                    .foregroundStyle(Color.error)
            }
        }
        .id(Self.kcalRowID)
    }

    private func numberRow(title: String, placeholder: String, text: Binding<String>) -> some View {
        HStack {
            Text(title)
                .layoutPriority(1)
            Spacer()
            TextField(placeholder, text: text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .focused($isKcalFocused)
            Text(L10n.Common.unitKcal)
                .foregroundStyle(Color.secondary)
        }
    }

    private func macroRow(title: String, macro: GoalMacro, text: Binding<String>, grams: Double?) -> some View {
        HStack(spacing: 8) {
            Text(title)
            Spacer()
            if let grams {
                Text(grams.formattedGrams(fractionDigits: 0))
                    .font(.footnote)
                    .foregroundStyle(Color.secondary)
            }
            stepButton(.minusCircle, macro: macro, direction: -1)
            TextField("", text: text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.center)
                .frame(width: 52)
            stepButton(.plusCircle, macro: macro, direction: 1)
            Text(viewModel.macroMode == .percent ? "%" : L10n.Common.unitGrams)
                .foregroundStyle(Color.secondary)
                .frame(width: 18, alignment: .leading)
        }
    }

    private func stepButton(_ imageName: BaseImageName, macro: GoalMacro, direction: Int) -> some View {
        Button {
            viewModel.onStep(macro, direction: direction)
        } label: {
            Image(systemName: imageName.rawValue)
                .font(.title3)
                .foregroundStyle(Color.accentColor)
        }
        .buttonStyle(.borderless)
    }

    private func infoRow(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(Color.secondary)
    }

    private func valueRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(Color.secondary)
        }
    }
}

extension GoalActivityLevel {

    // MARK: - Properties

    var title: String {
        switch self {
        case .sedentary: L10n.Goals.activitySedentary
        case .light: L10n.Goals.activityLight
        case .moderate: L10n.Goals.activityModerate
        case .high: L10n.Goals.activityHigh
        case .veryHigh: L10n.Goals.activityVeryHigh
        }
    }

    var detail: String {
        switch self {
        case .sedentary: L10n.Goals.activitySedentaryDetail
        case .light: L10n.Goals.activityLightDetail
        case .moderate: L10n.Goals.activityModerateDetail
        case .high: L10n.Goals.activityHighDetail
        case .veryHigh: L10n.Goals.activityVeryHighDetail
        }
    }
}

extension GoalVariant {

    // MARK: - Properties

    var shortTitle: String {
        if percentChange > 0 {
            "+\(percentChange) %"
        } else if percentChange < 0 {
            "\(percentChange) %"
        } else {
            L10n.Goals.variantMaintain
        }
    }

    var title: String {
        if percentChange > 0 {
            "\(L10n.Goals.variantGain) +\(percentChange) %"
        } else if percentChange < 0 {
            "\(L10n.Goals.variantLose) \(percentChange) %"
        } else {
            L10n.Goals.variantMaintain
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview("First goal") {
    NavigationStack {
        DailyGoalsView(
            viewModel: DailyGoalsViewModel(
                history: .empty,
                unit: .kg,
                fetchBodyWeights: FetchBodyWeightsUseCaseFake(),
                saveDailyGoal: SaveDailyGoalUseCaseFake(),
                saveBodyWeight: SaveBodyWeightUseCaseFake()
            ) { _ in }
        )
    }
}

#Preview("Existing goal") {
    var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2300, macroMode: .percent)
    goal.proteinPercent = 25
    goal.carbsPercent = 45
    goal.fatPercent = 30
    goal.sex = .male
    goal.birthYear = 1990
    goal.heightCm = 180
    goal.weightKg = 80
    goal.activityLevel = .moderate
    return NavigationStack {
        DailyGoalsView(
            viewModel: DailyGoalsViewModel(
                history: DailyGoalHistory(entries: [goal]),
                unit: .kg,
                fetchBodyWeights: FetchBodyWeightsUseCaseFake(),
                saveDailyGoal: SaveDailyGoalUseCaseFake(),
                saveBodyWeight: SaveBodyWeightUseCaseFake()
            ) { _ in }
        )
    }
}
#endif

private struct SliderLabelsLayout: Layout {
    // MARK: - Properties

    private let thumbInset: CGFloat = 21

    // MARK: - Functions

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let height = subviews.map { $0.sizeThatFits(.unspecified).height }.max() ?? 0
        return CGSize(width: proposal.width ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let lastIndex = max(subviews.count - 1, 1)
        let travel = bounds.width - 2 * thumbInset
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            let thumbCenter = thumbInset + CGFloat(index) / CGFloat(lastIndex) * travel
            let center = min(max(thumbCenter, size.width / 2), bounds.width - size.width / 2)
            subview.place(
                at: CGPoint(x: bounds.minX + center, y: bounds.midY),
                anchor: .center,
                proposal: .unspecified
            )
        }
    }
}
