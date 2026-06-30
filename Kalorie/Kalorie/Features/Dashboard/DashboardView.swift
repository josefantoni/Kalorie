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
            if viewModel.foodsConsumed.isEmpty {
                ContentUnavailableView(label: {
                    Label(L10n.Dashboard.emptyTitle, systemImage: "list.bullet.rectangle.portrait")
                        .padding()
                }, description: {
                    Text(L10n.Dashboard.emptyDescription)
                }, actions: {
                    Button(L10n.Dashboard.emptyAddFood) {
                        viewModel.showAddFoodSheet.toggle()
                    }
                })
                .padding()
            }
        }
        .sheet(isPresented: $viewModel.showAddFoodSheet) {
            router.makeAddFoodSheetView()
        }

        Button {
        } label: {
            BaseImage(
                imageName: .plusCircle,
                imageSize: .extraLarge
            )
        }
        .task { await viewModel.onAppear() }
    }
}

// MARK: - Preview

#Preview {
    DashboardConfigurator().createView()
}
