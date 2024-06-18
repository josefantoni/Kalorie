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
    
    @ObservedObject var viewModel: DashboardViewModel

    
    // MARK: - Init
    
    init(viewModel: DashboardViewModel) {
        self.viewModel = viewModel
    }
    
    
    // MARK: - Body

    var body: some View {
        HStack {
            Spacer()
            Button(action: {
                viewModel.state.showMealTypeSheet.toggle()
            }, label: {
                Text("rozvržení jídel")
            })
            .sheet(isPresented: $viewModel.state.showMealTypeSheet) {
                let state = MealTypeSheetViewState(
                    mealTypes: self.viewModel.state.mealTypes,
                    container: self.viewModel.state.container
                )
                let viewModel = MealTypeSheetViewModel(state)
                MealTypeSheetView(viewModel: viewModel)
            }
            .environmentObject(viewModel)
        }
        .padding(.trailing)
        
        List($viewModel.state.foodItems, id: \.id, editActions: .move) { food in
          // List item UI
            FoodItemView(food.wrappedValue)
        }
        .overlay {
            if viewModel.state.foodItems.isEmpty {
                ContentUnavailableView(label: {
                    Label("Žádné záznamy jídel", systemImage: "list.bullet.rectangle.portrait")
                    .padding()
                }, description: {
                    Text("Ještě jste dnes nic nesnědli, přidejme nějaké jídlo")
                }, actions: {
                    Button("Přidat jídlo") {
                        // TODO: Přidat jídlo
                    }
                })
                .padding()
            }
        }
    }
}


// MARK: - Preview

#Preview {
    let container = PersistentContainer.container
    let viewModel = DashboardViewModel(container: container)
    viewModel.state.foodItems = [
        DemoData.demoFoodItem(on: container.viewContext)
    ]
    PersistentContainer.save(container: container)
    return DashboardView(viewModel: viewModel)
}
