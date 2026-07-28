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
    let router: DashboardRouter

    // MARK: - Init

    init(viewModel: DashboardViewModel, router: DashboardRouter) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.router = router
    }

    // MARK: - Body

    var body: some View {
        HStack {
            Spacer()
            Button {
                viewModel.showMealTypeSheet.toggle()
            } label: {
                Text(L10n.Dashboard.buttonMealLayout)
            }
            .sheet(isPresented: $viewModel.showMealTypeSheet) {
                router.makeMealTypeSheetView(mealTypes: viewModel.mealTypes) {
                    Task { await viewModel.onMealTypesChanged() }
                }
            }
        }
        .padding(.trailing)

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
                        FoodConsumedView(food)
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
        .refreshable { await viewModel.onAppear() }
        .overlay {
            if viewModel.foodsConsumed.isEmpty && !viewModel.state.isLoading {
                emptyStateView
            }
        }
        .loader(viewModel.state.isLoading)
        .sheet(isPresented: $viewModel.showAddFoodSheet) {
            router.makeAddFoodSheetView(for: viewModel.selectedDay) {
                Task { await viewModel.onAppear() }
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .task { await viewModel.onAppear() }

        if !viewModel.foodsConsumed.isEmpty {
            Button {
                viewModel.showAddFoodSheet.toggle()
            } label: {
                BaseImage(
                    imageName: .plusCircle,
                    imageSize: .extraLarge
                )
            }
        }
    }

    // MARK: - Functions

    private var emptyStateView: some View {
        VStack(spacing: 25) {
            Label {
                Text(L10n.Dashboard.emptyTitle)
            } icon: {
                Image(systemName: "list.bullet.rectangle.portrait")
            }
            .font(.title2)

            Text(L10n.Dashboard.emptyDescription)
                .font(.subheadline)
                .foregroundStyle(.secondary)
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
    DashboardConfigurator().createView()
}

#Preview {
    let cal = Calendar.current
    let now = Date.now
    func time(hour: Int) -> Date { cal.date(bySettingHour: hour, minute: 0, second: 0, of: now) ?? now }

    let viewModel = DashboardViewModel(
        fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [
            MealTypeDomain(id: 0, name: "Snídaně", startTime: time(hour: 7), endTime: time(hour: 10)),
            MealTypeDomain(id: 1, name: "Oběd", startTime: time(hour: 11), endTime: time(hour: 14))
        ]),
        fetchFoodsConsumed: FetchFoodsConsumedUseCaseFake(stubbedFoods: [
            FoodConsumedDomain(id: "1", czName: "Ovesné vločky", engName: "Oats", weight: 80, date: time(hour: 8), calories: 295, protein: 10, carbohydrate: 52, carbohydrateSugar: 8, fat: 5, fatUnsaturated: 2, fiber: 6, salt: 0.1),
            FoodConsumedDomain(id: "2", czName: "Kuřecí prsa", engName: "Chicken breast", weight: 150, date: time(hour: 12), calories: 165, protein: 31, carbohydrate: 0, carbohydrateSugar: 0, fat: 3.5, fatUnsaturated: 1.2, fiber: 0, salt: 0.2),
            FoodConsumedDomain(id: "3", czName: "Rýže", engName: "Rice", weight: 200, date: time(hour: 12), calories: 260, protein: 5, carbohydrate: 57, carbohydrateSugar: 0.4, fat: 0.4, fatUnsaturated: 0.2, fiber: 1, salt: 0)
        ]),
        setupDefaultMeals: SetupDefaultMealsUseCaseFake()
    )

    let dataProvider = FirestoreDataProvider()
    let authProvider = AuthProvider()
    return DashboardView(
        viewModel: viewModel,
        router: DashboardRouter(
            mealTypeSheetConfigurator: MealTypeSheetConfigurator(),
            addFoodSheetConfigurator: AddFoodSheetConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        )
    )
}
