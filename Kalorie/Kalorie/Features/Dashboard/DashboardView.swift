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
    @State private var macroPopoverIndex: Int?
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
            List {
                if !viewModel.foodsConsumed.isEmpty {
                    MacroSummaryView(macros: viewModel.dailyMacros)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }

                ForEach(Array(viewModel.groupedFoods.enumerated()), id: \.offset) { index, group in
                    Section {
                        ForEach(group.foods, id: \.id) { food in
                            NavigationLink(value: food) {
                                FoodConsumedView(food)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewModel.onDeleteRequested(food)
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                        }
                    } header: {
                        HStack(spacing: 4) {
                            Text(group.mealType?.name ?? L10n.Dashboard.sectionUnassignedFoods)
                            Spacer()
                            Button {
                                macroPopoverIndex = index
                            } label: {
                                Image(systemName: "info.circle")
                                    .font(.title2)
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                            .popover(isPresented: Binding(
                                get: { macroPopoverIndex == index },
                                set: { if !$0 { macroPopoverIndex = nil } }
                            )) {
                                MealSectionMacroView(
                                    name: group.mealType?.name ?? L10n.Dashboard.sectionUnassignedFoods,
                                    foods: group.foods
                                )
                                .presentationCompactAdaptation(.popover)
                            }
                        }
                        .font(.subheadline)
                        .padding(.leading, -5)
                    }
                }
            }
            .refreshable { await viewModel.onRefresh() }
            .navigationDestination(for: FoodConsumedDomain.self) { food in
                router.makeFoodConsumedDetailView(food: food) {
                    Task { await viewModel.onFoodConsumedUpdated() }
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
                    .background(Color.accentColor)
                    .clipShape(.circle)
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
                        Image(systemName: "person.circle")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        viewModel.showMealTypeSheet.toggle()
                    } label: {
                        Image(systemName: "list.bullet.circle")
                    }
                    .accessibilityLabel(L10n.Dashboard.buttonMealLayout)
                }
            }
            .sheet(isPresented: $viewModel.showMealTypeSheet) {
                router.makeMealTypeSheetView(mealTypes: viewModel.mealTypes) {
                    Task { await viewModel.onMealTypesChanged() }
                }
            }
            .sheet(isPresented: $viewModel.showAddFoodSheet) {
                viewModel.onAddFoodSheetDismissed()
            } content: {
                router.makeAddFoodSheetView(for: viewModel.selectedDay, onFoodSaved: {
                    Task { await viewModel.onFoodConsumedUpdated() }
                }) {
                    viewModel.onCreateMealRequested()
                }
            }
            .sheet(isPresented: $viewModel.showAccountSheet) {
                router.makeAccountView()
            }
            .sheet(isPresented: $viewModel.showMyCreatedMealEditor) {
                NavigationStack {
                    router.makeMyCreatedMealEditorView()
                }
            }
            .sheet(isPresented: $viewModel.showCalendarSheet) {
                MonthCalendarView(
                    selectedDay: viewModel.selectedDay,
                    activeDays: viewModel.activeDaysInMonth,
                    onDaySelected: { day in Task { await viewModel.onDaySelected(day) } },
                    onMonthChanged: { month in Task { await viewModel.onCalendarMonthChanged(to: month) } }
                )
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
            .task { await viewModel.onAppear() }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    Task { await viewModel.onRefresh() }
                }
            }
        }
    }

    // MARK: - Functions

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
                Image(systemName: "list.bullet.rectangle.portrait")
            }
            .font(.title2)

            Text(kind == .today ? L10n.Dashboard.emptyDescription : kind == .past ? L10n.Dashboard.emptyDescriptionPast : L10n.Dashboard.emptyDescriptionFuture)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.bottom)

            Button(L10n.Dashboard.emptyAddFood) {
                viewModel.showAddFoodSheet.toggle()
            }
            .buttonStyle(.borderedProminent)
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
            MealTypeDomain(id: "0", name: "Snídaně", startTime: time(hour: 7), endTime: time(hour: 10)),
            MealTypeDomain(id: "1", name: "Oběd", startTime: time(hour: 11), endTime: time(hour: 14))
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
                protein: 10,
                carbohydrate: 52,
                carbohydrateSugar: 8,
                fat: 5,
                fatUnsaturated: 2,
                fiber: 6,
                salt: 0.1
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
                protein: 31,
                carbohydrate: 0,
                carbohydrateSugar: 0,
                fat: 3.5,
                fatUnsaturated: 1.2,
                fiber: 0,
                salt: 0.2
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
                protein: 5,
                carbohydrate: 57,
                carbohydrateSugar: 0.4,
                fat: 0.4,
                fatUnsaturated: 0.2,
                fiber: 1,
                salt: 0
            )
        ]),
        setupDefaultMeals: SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseFake(),
        deleteFoodConsumed: DeleteFoodConsumedUseCaseFake()
    )

    let dataProvider = FirestoreDataProvider()
    let authProvider = AuthProvider()
    return DashboardView(
        viewModel: viewModel,
        router: DashboardRouter(
            mealTypeSheetConfigurator: MealTypeSheetConfigurator(),
            addFoodSheetConfigurator: AddFoodSheetConfigurator(dataProvider: dataProvider, authProvider: authProvider),
            foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator(dataProvider: dataProvider, authProvider: authProvider),
            accountConfigurator: AccountConfigurator(dataProvider: dataProvider, authProvider: authProvider, mergeStatusReporting: MergeStatusReportingFake()),
            myCreatedMealEditorConfigurator: MyCreatedMealEditorConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        )
    )
}
