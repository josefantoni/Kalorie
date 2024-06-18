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
            Section {
                FoodItemView(food.wrappedValue)
            }
            header: {
                Text("Ostatní nezařazená jídla")
                    .frame(
                        maxWidth: .infinity,
                        alignment: .leading
                    )
                    .font(.subheadline)
                    .padding(.leading, -5)

                Button {
                    viewModel.state.showAddFoodSheet = (
                        isVisible: true,
                        screenType: .barCode
                    )
                } label: {
                    Image(systemName: "barcode.viewfinder")
                        .foregroundColor(.green)
                }
                .font(.title)
                
                Button {
                    viewModel.state.showAddFoodSheet = (
                        isVisible: true,
                        screenType: .base
                    )
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .foregroundColor(.green)
                }
                .font(.title)
                .padding(.trailing, -25)
            }
        }
        .listStyle(.sidebar)
        .overlay {
            if viewModel.state.foodItems.isEmpty {
                ContentUnavailableView(label: {
                    Label("Žádné záznamy jídel", systemImage: "list.bullet.rectangle.portrait")
                    .padding()
                }, description: {
                    Text("Ještě jste dnes nic nesnědli, přidejme nějaké jídlo")
                }, actions: {
                    Button("Přidat jídlo") {
                        viewModel.state.showAddFoodSheet.isVisible.toggle()
                    }
                })
                .padding()
            }
        }
        .sheet(isPresented: $viewModel.state.showAddFoodSheet.isVisible) {
            switch viewModel.state.showAddFoodSheet.screenType {
            case .base:
                AddFoodSheetView(withBarcodeScan: false)
            case .barCode:
                AddFoodSheetView(withBarcodeScan: true)
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
