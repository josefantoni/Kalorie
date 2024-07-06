//
//  MealTypeSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI


struct MealTypeSheetView: View {
    
    // MARK: - Properties
    
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @ObservedObject var viewModel: MealTypeSheetViewModel
    @FocusState private var focusedField: Field? // resigning
    @Environment(\.dismiss) var dismiss

    private enum Field: Int, CaseIterable {
        case newMealName
    }
    
    
    // MARK: - Init
    
    init(viewModel: MealTypeSheetViewModel) {
        self.viewModel = viewModel
    }
    
    
    // MARK: - Body

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                List {
                    Section(
                        header: Text("Rozvržení jídel"),
                        footer: footerView
                            .padding([.leading, .trailing], -20)
                            .padding([.top], 20)
                    ) {
                        ForEach($viewModel.state.mealTypes, id: \.id, editActions: .move) { mealType in
                            MealTypeItemView(mealType.wrappedValue)
                        }
                        .onDelete { indexSet in
                            if let index = indexSet.first {
                                dashboardViewModel.state.mealTypes.remove(at: index)
                                viewModel.delete(by: index)
                            }
                        }
                    }
                }
            }
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .topTrailing
            )
            .toolbar {
                dismissButton
            }
        }
    }
        
    @ViewBuilder var footerView: some View {
        if viewModel.state.isAddButtonHidden {
            BaseButton(
                style: .plain,
                imageName: .plusCircle,
                imageSize: .extraLarge
            ) {
                let time = viewModel.getPossibleTimeForNewMealCreation()
                showAddButton(
                    possibleStart: time.possibleStart,
                    possibleEnd: time.possibleEnd
                )
            }
            .frame(maxWidth: .infinity)
        } else {
            VStack {
                VStack {
                    TextField("Druhá večeře nebo přesnídávka", text: $viewModel.state.newMealName)
                        .padding([.leading, .trailing], 20)
                        .font(.system(size: 20))
                        .padding(.top, 20)
                        .padding(.bottom, 10)
                        .focused($focusedField, equals: .newMealName)
                    Divider()

                    HStack {
                        DatePicker("Od", selection: $viewModel.state.newMealStart, displayedComponents: .hourAndMinute)
                            .datePickerStyle(GraphicalDatePickerStyle())
                            .onChange(of: viewModel.state.newMealStart) {
                                if viewModel.state.newMealStart >= viewModel.state.newMealEnd {
                                    viewModel.state.newMealEnd = viewModel.state.newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                        Divider()
                        DatePicker("Do", selection: $viewModel.state.newMealEnd, displayedComponents: .hourAndMinute)
                            .datePickerStyle(CompactDatePickerStyle())
                            .onChange(of: viewModel.state.newMealEnd) {
                                if viewModel.state.newMealStart >= viewModel.state.newMealEnd {
                                    viewModel.state.newMealEnd = viewModel.state.newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                    }
                    .padding([.leading, .trailing], 20)
                    .padding(.top, 10)
                    .font(.system(size: 20))
                }
                .padding(.bottom, 20)
                .background(.white)

                Button {
                    if let createdMealType = viewModel.createMealType() {
                        dashboardViewModel.state.mealTypes.append(createdMealType)
                    }
                    focusedField = nil
                } label: {
                    Text("Vytvořit")
                        .padding()
                        .frame(maxWidth: .infinity)
                }
                .alert(isPresented: $viewModel.state.showingAlert) {
                    Alert(
                        title: Text(viewModel.state.alertTitle),
                        dismissButton: Alert.Button.default(Text("Dobrá"))
                    )
                }
                .foregroundColor(.white)
                .background(.blue)
                .frame(maxWidth: .infinity)
                .padding(.top, -10)
                .font(.system(size: 20, weight: .bold))
            }
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(.blue, lineWidth: 1)
            )
        }
    }
    
    var dismissButton: ToolbarItem<(), some View> {
        ToolbarItem(placement: .topBarTrailing) {
            BaseButton(
                style: .plain,
                imageName: .close,
                imageSize: .basicPlus
            ) {
                dismiss()
            }
            .padding(.top, 10)
        }
    }

    
    // MARK: - Functions
    
    func showAddButton(possibleStart: Date, possibleEnd: Date) {
        viewModel.state.newMealStart = possibleStart
        viewModel.state.newMealEnd = possibleEnd
        viewModel.state.isAddButtonHidden = false
    }
}


// MARK: - Preview

#Preview {
    let container = PersistentContainer.container
    let state = MealTypeSheetViewState(
        mealTypes: DemoData.demoMeals(on: container.viewContext), 
        container: container
    )
    let viewModel = MealTypeSheetViewModel(state)
    return MealTypeSheetView(viewModel: viewModel)
}
