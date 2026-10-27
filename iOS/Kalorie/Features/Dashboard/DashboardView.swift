//
//  DashboardView.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI

struct DashboardView: View {

    // MARK: - Properties

    @StateObject var viewModel: DashboardViewModel
    @State private var pulseAnimation = false
    @State private var accountButtonFrame: CGRect = .zero
    @State private var targetedMealTypeId: String?
    @State private var openSwipeRowId: String?
    @Environment(\.scenePhase) private var scenePhase
    let router: DashboardRouter

    // MARK: - Init

    init(viewModel: DashboardViewModel, router: DashboardRouter) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.router = router
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 30) {
                    if !viewModel.foodsConsumed.isEmpty {
                        MacroSummaryView(macros: viewModel.dailyMacros) {
                            weightAccessory
                        }
                    }

                    ForEach(Array(viewModel.groupedFoods.enumerated()), id: \.element.mealType?.id) { index, group in
                        mealSection(index: index, group: group)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .background(Color(.systemGroupedBackground))
            .simultaneousGesture(TapGesture().onEnded { openSwipeRowId = nil })
            .onScrollPhaseChange { _, newPhase in
                if newPhase == .interacting { openSwipeRowId = nil }
            }
            .sensoryFeedback(.success, trigger: viewModel.moveFeedbackTrigger)
            .refreshable { await viewModel.onRefresh() }
            .navigationDestination(for: FoodConsumedDomain.self) { food in
                router.makeFoodConsumedDetailView(food: food, mealTypes: viewModel.mealTypes) {
                    Task { await viewModel.onFoodConsumedUpdated() }
                }
            }
            .navigationDestination(isPresented: $viewModel.isWeightHistoryPushed) {
                router.makeBodyWeightHistoryView(unit: viewModel.appSettings.bodyWeightUnit) {
                    Task { await viewModel.onWeightsChanged() }
                }
            }
            .overlay {
                if viewModel.foodsConsumed.isEmpty && !viewModel.state.isLoading {
                    emptyStateView
                }
            }
            .safeAreaInset(edge: .top) {
                DayPickerView(
                    selectedDay: $viewModel.selectedDay,
                    activeDays: viewModel.activeDaysInMonth,
                    onDayChanged: { day in Task { await viewModel.onDayChanged(day) } },
                    onTapSelectedDay: { viewModel.showCalendarSheet = true }
                )
                .padding(.horizontal)
                .padding(.top, 8)
            }
            .safeAreaInset(edge: .bottom) {
                if !viewModel.foodsConsumed.isEmpty {
                    Button {
                        viewModel.showAddFoodSheet.toggle()
                    } label: {
                        Image(systemName: BaseImageName.plus.rawValue)
                            .font(.title2)
                            .fontWeight(.semibold)
                            .foregroundStyle(.white)
                            .padding(20)
                    }
                    .glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)
                    .padding(.bottom, 8)
                }
            }
            .loader(viewModel.state.isLoading)
            .toolbarBackground(.hidden, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        viewModel.showAccountSheet.toggle()
                    } label: {
                        Image(systemName: BaseImageName.personCircle.rawValue)
                    }
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { accountButtonFrame = $0 }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        viewModel.showSettings.toggle()
                    } label: {
                        Image(systemName: BaseImageName.gearshape.rawValue)
                    }
                    .accessibilityLabel(L10n.Dashboard.buttonSettings)
                }
            }
            .sheet(isPresented: $viewModel.showSettings) {
                router.makeSettingsView(mealTypes: viewModel.mealTypes) {
                    Task { await viewModel.onMealTypesChanged() }
                }
                .dynamicTypeLimit()
            }
            .sheet(isPresented: $viewModel.showAddFoodSheet, onDismiss: viewModel.onAddFoodSheetDismissed) {
                router.makeAddFoodSheetView(for: viewModel.selectedDay, mealTypes: viewModel.mealTypes) {
                    Task { await viewModel.onFoodConsumedUpdated() }
                }
                .dynamicTypeLimit()
            }
            .sheet(isPresented: $viewModel.showAccountSheet) {
                router.makeAccountView()
                    .dynamicTypeLimit()
            }
            .sheet(isPresented: $viewModel.showCalendarSheet, onDismiss: viewModel.onCalendarDismissed) {
                MonthCalendarView(
                    selectedDay: viewModel.selectedDay,
                    activeDays: viewModel.activeDaysInMonth,
                    onDaySelected: { day in Task { await viewModel.onDaySelected(day) } },
                    onMonthChanged: { month in Task { await viewModel.onCalendarMonthChanged(to: month) } }
                )
                .dynamicTypeLimit()
            }
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
                    dismissButton: .default(Text(L10n.Common.ok))
                )
            }
            .alert(L10n.Dashboard.confirmDeleteFood, isPresented: $viewModel.isDeleteConfirmationVisible) {
                Button(L10n.Common.buttonNo, role: .cancel) {}
                Button(L10n.Common.buttonYes, role: .destructive) {
                    Task { await viewModel.onDeleteConfirmed() }
                }
            }
            .task {
                await viewModel.onAppear()
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    Task { await viewModel.onForeground() }
                }
            }
        }
        .overlay {
            if viewModel.isSignInSpotlightVisible {
                SignInSpotlightView(
                    targetFrame: accountButtonFrame,
                    onSignIn: viewModel.onSignInSpotlightSignInTapped,
                    onDismiss: viewModel.onSignInSpotlightDismissed
                )
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.isSignInSpotlightVisible)
    }

    // MARK: - Functions

    @ViewBuilder
    private func mealSection(index: Int, group: (mealType: MealTypeDomain?, foods: [FoodConsumedDomain])) -> some View {
        let isDropTarget = group.mealType != nil && targetedMealTypeId == group.mealType?.id
        VStack(alignment: .leading, spacing: 10) {
            Text(group.mealType?.name ?? L10n.Dashboard.sectionUnassignedFoods)
                .font(.headline)
                .foregroundStyle(.secondary)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(alignment: .trailing) {
                    sectionHeaderButtons(index: index, group: group)
                }
                .padding(.horizontal, 16)

            VStack(spacing: 0) {
                ForEach(Array(group.foods.enumerated()), id: \.element.id) { foodIndex, food in
                    foodRow(food)
                    if foodIndex < group.foods.count - 1 {
                        Divider().padding(.horizontal, 16)
                    }
                }
            }
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 24))
            .clipShape(.rect(cornerRadius: 24))
            .overlay {
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color.accentColor, lineWidth: isDropTarget ? 3 : 0)
            }
        }
        .onDrop(
            of: group.mealType == nil ? [] : [.kalorieFoodConsumed],
            isTargeted: Binding(
                get: { isDropTarget },
                set: { isTargeted in
                    if isTargeted {
                        targetedMealTypeId = group.mealType?.id
                    } else if targetedMealTypeId == group.mealType?.id {
                        targetedMealTypeId = nil
                    }
                }
            )
        ) { providers in
            guard let mealType = group.mealType else { return false }
            viewModel.onFoodsDropped(providers, on: mealType)
            return true
        }
    }

    private func foodRow(_ food: FoodConsumedDomain) -> some View {
        RevealingSwipeToDeleteRow(id: food.id, openRowId: $openSwipeRowId) {
            viewModel.onDeleteRequested(food)
        } content: {
            NavigationLink(value: food) {
                HStack(spacing: 12) {
                    FoodConsumedView(food)
                    Image(systemName: BaseImageName.chevronRight.rawValue)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 15)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .allowsHitTesting(openSwipeRowId == nil)
            .onDrag {
                DraggedFoodConsumed(id: food.id).itemProvider
            } preview: {
                FoodConsumedView(food)
            }
        }
    }

    @ViewBuilder private var weightAccessory: some View {
        if viewModel.isWeightCircleVisible {
            weightCircle(unit: viewModel.appSettings.bodyWeightUnit)
        }
    }

    private func weightCircle(unit: BodyWeightUnit) -> some View {
        Button {
            viewModel.isWeightHistoryPushed = true
        } label: {
            WeightCircleView(weights: viewModel.recentWeights, unit: unit)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(weightAccessibilityLabel(unit: unit))
            .overlay(alignment: .topTrailing) {
                if viewModel.canLogWeight {
                    weightBadge(unit: unit)
                }
            }
    }

    private func weightAccessibilityLabel(unit: BodyWeightUnit) -> String {
        guard let latest = viewModel.recentWeights.first else { return L10n.Dashboard.weightA11yEmpty }
        return L10n.Dashboard.weightA11yValue(unit.formatted(kilograms: latest.kilograms))
    }

    private var weightBadgeImageName: String {
        viewModel.hasWeightForSelectedDay ? BaseImageName.pencil.rawValue : BaseImageName.plus.rawValue
    }

    private func weightBadge(unit: BodyWeightUnit) -> some View {
        Button {
            viewModel.onWeightBadgeTapped()
        } label: {
            Image(systemName: weightBadgeImageName)
                .font(.caption.bold())
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(Color.accentColor, in: Circle())
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .offset(x: 14, y: -14)
        .accessibilityLabel(viewModel.hasWeightForSelectedDay ? L10n.Dashboard.weightBadgeEdit : L10n.Dashboard.weightBadgeLog)
        .popover(isPresented: $viewModel.isWeightPopoverVisible) {
            weightPopover(unit: unit)
        }
    }

    private func weightPopover(unit: BodyWeightUnit) -> some View {
        VStack(spacing: 12) {
            WeightPickerView(kilograms: $viewModel.weightPickerKilograms, unit: unit)
            weightConfirmButton
        }
        .padding()
        .frame(width: 280)
        .interactiveDismissDisabled(viewModel.isSavingWeight || viewModel.showWeightCheckmark)
        .dynamicTypeLimit()
        .presentationCompactAdaptation(.popover)
    }

    private var weightConfirmButton: some View {
        Button {
            Task { await viewModel.onWeightConfirmed() }
        } label: {
            weightConfirmLabel
        }
        .buttonStyle(.glassProminent)
        .tint(viewModel.showWeightCheckmark ? Color.success : Color.accentColor)
        .animation(.spring(duration: 0.4), value: viewModel.showWeightCheckmark)
        .disabled(viewModel.isSavingWeight || viewModel.showWeightCheckmark)
    }

    @ViewBuilder private var weightConfirmLabel: some View {
        if viewModel.showWeightCheckmark {
            Image(systemName: BaseImageName.checkmark.rawValue)
                .transition(.scale.combined(with: .opacity))
        } else {
            Text(L10n.Common.buttonDone)
                .transition(.opacity)
        }
    }

    private func sectionHeaderButtons(index: Int, group: (mealType: MealTypeDomain?, foods: [FoodConsumedDomain])) -> some View {
        HStack(spacing: 4) {
            Button {
                viewModel.onCopyRequested(from: group.mealType, at: index)
            } label: {
                Image(systemName: BaseImageName.docOnDoc.rawValue)
                    .foregroundStyle(Color(.label))
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .accessibilityLabel(L10n.Dashboard.buttonCopy)
            .popover(isPresented: Binding(
                get: { viewModel.copyPopoverIndex == index },
                set: { if !$0 { viewModel.copyPopoverIndex = nil } }
            )) {
                MealSectionCopyView(
                    viewModel: viewModel,
                    name: group.mealType?.name ?? L10n.Dashboard.sectionUnassignedFoods,
                    mealType: group.mealType,
                    foods: group.foods
                )
                .dynamicTypeLimit()
                .presentationCompactAdaptation(.popover)
            }
            .padding(.trailing, 8)
            Button {
                viewModel.macroPopoverIndex = index
            } label: {
                Image(systemName: BaseImageName.infoCircle.rawValue)
                    .foregroundStyle(Color(.label))
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .popover(isPresented: Binding(
                get: { viewModel.macroPopoverIndex == index },
                set: { if !$0 { viewModel.macroPopoverIndex = nil } }
            )) {
                MealSectionMacroView(
                    name: group.mealType?.name ?? L10n.Dashboard.sectionUnassignedFoods,
                    foods: group.foods
                )
                .dynamicTypeLimit()
                .presentationCompactAdaptation(.popover)
            }
        }
        .controlSize(.small)
        .offset(y: -7)
    }

    private enum SelectedDayKind {
        case past, today, future
    }

    private var selectedDayKind: SelectedDayKind {
        let calendar = Calendar.current
        if calendar.isDateInToday(viewModel.selectedDay) {
            return .today
        } else if viewModel.selectedDay < Date.now {
            return .past
        } else {
            return .future
        }
    }

    private var emptyStateView: some View {
        let kind = selectedDayKind
        return VStack(spacing: 25) {
            Label {
                Text(kind == .today ? L10n.Dashboard.emptyTitle : kind == .past ? L10n.Dashboard.emptyTitlePast : L10n.Dashboard.emptyTitleFuture)
            } icon: {
                Image(systemName: BaseImageName.listBulletRectanglePortrait.rawValue)
            }
            .font(.title2)
            .multilineTextAlignment(.center)

            Text(kind == .today ? L10n.Dashboard.emptyDescription : kind == .past ? L10n.Dashboard.emptyDescriptionPast : L10n.Dashboard.emptyDescriptionFuture)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.bottom)

            Button(L10n.Dashboard.emptyAddFood) {
                viewModel.showAddFoodSheet.toggle()
            }
            .buttonStyle(.glassProminent)
            .scaleEffect(pulseAnimation ? 1.2 : 1.0)
            .animation(.easeInOut(duration: 0.7), value: pulseAnimation)
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3))
                pulseAnimation = true
                try? await Task.sleep(for: .seconds(0.7))
                pulseAnimation = false
            }
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    DashboardConfigurator().createView(mergeStatusReporting: MergeStatusReportingFake())
}

#Preview {
    let cal = Calendar.current
    let now = Date.now
    func time(hour: Int) -> Date { cal.date(bySettingHour: hour, minute: 0, second: 0, of: now) ?? now }

    let viewModel = DashboardViewModel(
        fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [
            MealTypeDomain(id: "0", name: "Snídaně", startMinutes: 7 * 60, endMinutes: 10 * 60),
            MealTypeDomain(id: "1", name: "Oběd", startMinutes: 11 * 60, endMinutes: 14 * 60)
        ]),
        fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: [
            FoodConsumedDomain(
                id: "1",
                foodItemId: "1",
                foodItemKind: .catalogue,
                czName: "Ovesné vločky",
                engName: "Oats",
                weight: 80,
                date: time(hour: 8),
                calories: 295,
                caloriesPerHundredGrams: 368.75,
                energyKJ: 1544,
                protein: 10,
                carbohydrate: 52,
                carbohydrateSugar: 8,
                fat: 5,
                fatSaturated: 1,
                fatUnsaturated: 2,
                fiber: 6,
                salt: 0.1,
                mealTypeId: nil
            ),
            FoodConsumedDomain(
                id: "2",
                foodItemId: "2",
                foodItemKind: .catalogue,
                czName: "Kuřecí prsa",
                engName: "Chicken breast",
                weight: 150,
                date: time(hour: 12),
                calories: 165,
                caloriesPerHundredGrams: 110,
                energyKJ: 690,
                protein: 31,
                carbohydrate: 0,
                carbohydrateSugar: 0,
                fat: 3.5,
                fatSaturated: 1,
                fatUnsaturated: 1.2,
                fiber: 0,
                salt: 0.2,
                mealTypeId: nil
            ),
            FoodConsumedDomain(
                id: "3",
                foodItemId: "3",
                foodItemKind: .catalogue,
                czName: "Rýže",
                engName: "Rice",
                weight: 200,
                date: time(hour: 12),
                calories: 260,
                caloriesPerHundredGrams: 130,
                energyKJ: 546,
                protein: 5,
                carbohydrate: 57,
                carbohydrateSugar: 0.4,
                fat: 0.4,
                fatSaturated: 0.1,
                fatUnsaturated: 0.2,
                fiber: 1,
                salt: 0,
                mealTypeId: nil
            )
        ]),
        fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseFake(),
        setupDefaultMeals: SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseFake(),
        deleteFoodConsumed: DeleteFoodConsumedUseCaseFake(),
        copyFoodsConsumed: CopyFoodsConsumedUseCaseFake(),
        assignFoodMealType: AssignFoodMealTypeUseCaseFake(),
        fetchBodyWeights: FetchBodyWeightsUseCaseFake(),
        fetchAppSettings: FetchAppSettingsUseCaseFake(),
        saveBodyWeight: SaveBodyWeightUseCaseFake(),
        authProvider: AuthProviderFake(),
        signInSpotlightStore: SignInSpotlightStoreFake()
    )

    let dataProvider = FirestoreDataProvider()
    let authProvider = AuthProvider()
    return DashboardView(
        viewModel: viewModel,
        router: DashboardRouter(
            settingsConfigurator: SettingsConfigurator(),
            addFoodSheetConfigurator: AddFoodSheetConfigurator(dataProvider: dataProvider, authProvider: authProvider),
            foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator(dataProvider: dataProvider, authProvider: authProvider),
            accountConfigurator: AccountConfigurator(dataProvider: dataProvider, authProvider: authProvider, mergeStatusReporting: MergeStatusReportingFake()),
            bodyWeightHistoryConfigurator: BodyWeightHistoryConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        )
    )
}
#endif
