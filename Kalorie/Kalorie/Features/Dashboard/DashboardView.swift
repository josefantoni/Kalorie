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
            .sheet(isPresented: $viewModel.showMealTypeSheet, onDismiss: {
                Task { await viewModel.onAppear() }
            }) {
                router.makeMealTypeSheetView(mealTypes: viewModel.mealTypes)
            }
        }
        .padding(.trailing)

        List($viewModel.foodsConsumed, id: \.id, editActions: .move) { food in
            Section {
                FoodConsumedView(food.wrappedValue)
            } header: {
                VStack {
                    Text(L10n.Dashboard.sectionUnassignedFoods)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.subheadline)
                        .padding(.leading, -5)
                }
            }
        }
        .overlay {
            if viewModel.foodsConsumed.isEmpty && !viewModel.state.isLoading {
                emptyStateView
            }
        }
        .loader(viewModel.state.isLoading)
        .sheet(isPresented: $viewModel.showAddFoodSheet) {
            router.makeAddFoodSheetView()
        }
        .alert(viewModel.alertTitle, isPresented: $viewModel.showingAlert) {
            Button(L10n.Common.ok) {}
        }
        .task { await viewModel.onAppear() }

        if !viewModel.foodsConsumed.isEmpty {
            Button {
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
