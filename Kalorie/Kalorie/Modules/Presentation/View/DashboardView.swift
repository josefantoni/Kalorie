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
            Button {
                viewModel.state.showMealTypeSheet.toggle()
            } label: {
                Text("rozvržení jídel")
            }
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
        
        List($viewModel.state.foodsConsumed, id: \.id, editActions: .move) { food in
          // List item UI
            Section {
                FoodConsumedView(food.wrappedValue)
            }
            header: {
                VStack {
                    Text("Ostatní nezařazená jídla")
                        .frame(
                            maxWidth: .infinity,
                            alignment: .leading
                        )
                        .font(.subheadline)
                        .padding(.leading, -5)
                    
//                    HStack {
//                        Button {
//                            viewModel.state.showAddFoodSheet = (
//                                isVisible: true,
//                                screenType: .barCode
//                            )
//                        } label: {
//                            Image(systemName: "barcode.viewfinder")
//                                .font(.system(size: 30))
//                                .frame(maxWidth: .infinity)
//                        }
//                        .clipShape(Capsule())
//                        .buttonStyle(.borderedProminent)
//
//                        Button {
//                            viewModel.state.showAddFoodSheet = (
//                                isVisible: true,
//                                screenType: .base
//                            )
//                        } label: {
//                            Image(systemName: "plus.circle")
//                                .font(.system(size: 30))
//                                .frame(maxWidth: .infinity)
//                        }
//                        .clipShape(Capsule())
//                        .buttonStyle(.borderedProminent)
//                    }
                }
            }
        }
        .overlay {
            if viewModel.state.foodsConsumed.isEmpty {
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
        
        Button {
            // override in 'highPriorityGesture'
        } label: {
            BaseImage(
                imageName: .plusCircle,
                imageSize: .extraLarge
            )
        }
        .simultaneousGesture(
                LongPressGesture()
                    .onEnded { _ in
                        print("Loooong")
                    }
            )
            .highPriorityGesture(
                TapGesture()
                    .onEnded { _ in
                        print("Tap")
                    }
            )
    }
}


// MARK: - Preview

#Preview {
    let container = PersistentContainer.container
    let viewModel = DashboardViewModel(container: container)
    viewModel.state.foodsConsumed = [
        DemoData.demofoodConsumed(on: container.viewContext)
    ]
    PersistentContainer.save(container: container)
    return DashboardView(viewModel: viewModel)
}
