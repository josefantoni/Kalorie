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
    @Environment(\.dismiss) var dismiss
//    @Environment(\.editMode) var editMode
    @ObservedObject var viewModel: MealTypeSheetViewModel
    @FocusState private var focusedField: Field? // resigning

    // Hidden UI
    @State private var isAddButtonHidden = true
    @State private var showingAlert = false
    
    // New meal creation
    @State private var alertTitle = ""
    @State private var newMealName = ""
    @State private var newMealStart = Date.now
    @State private var newMealEnd = Date.now

//    private var isEditing: Bool {
//       editMode?.wrappedValue.isEditing == true
//    }
    
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
                HStack(spacing: 0) {
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle")
                            .font(.largeTitle)
                    }
                    .padding([.top, .trailing])
                }
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
//                TODO: - changing of order
//                .environment(\.editMode, .constant(EditMode.active))
            }
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .topTrailing
            )
        }
//        TODO: - changing of order
//        .onChange(of: viewModel.state.mealTypes) { oldValue, newValue in
//            print("oldValue: \(oldValue)")
//            print("newValue: \(newValue)")
//            if let oldValueFirst = oldValue.first, let i = viewModel.state.mealTypes.firstIndex(where: { $0.id == oldValueFirst.id }) {
//                print("oldValueFirst: \(oldValueFirst)")
//                print("index of it: \(i)")
//            }
//        }
    }
        
    @ViewBuilder var footerView: some View {
        if isAddButtonHidden {
            Button {
                let time = dashboardViewModel.getPossibleTimeForNewMealCreation()
                newMealStart = time.possibleStart
                newMealEnd = time.possibleEnd
                isAddButtonHidden.toggle()
            } label: {
                Spacer()
                Image(systemName: "plus.circle.fill")
                    .foregroundColor(.green)
                    .font(.system(size: 60))
                    .padding(.top, 15)
                Spacer()
            }
        } else {
            VStack {
                VStack {
                    TextField("Druhá večeře nebo přesnídávka", text: $newMealName)
                        .padding([.leading, .trailing], 20)
                        .font(.system(size: 20))
                        .padding(.top, 20)
                        .padding(.bottom, 10)
                        .focused($focusedField, equals: .newMealName)
                    Divider()

                    HStack {
                        DatePicker("Od", selection: $newMealStart, displayedComponents: .hourAndMinute)
                            .datePickerStyle(GraphicalDatePickerStyle())
                            .onChange(of: newMealStart) {
                                if newMealStart >= newMealEnd {
                                    newMealEnd = newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                        Divider()
                        DatePicker("Do", selection: $newMealEnd, displayedComponents: .hourAndMinute)
                            .datePickerStyle(CompactDatePickerStyle())
                            .onChange(of: newMealEnd) {
                                if newMealStart >= newMealEnd {
                                    newMealEnd = newMealStart.withAddedMinutes(minutes: 30)
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
                    focusedField = nil

                    do {
                        let newMeal = try dashboardViewModel.createMealTypeIfPossible(
                            mealName: newMealName,
                            startTime: newMealStart,
                            endTime: newMealEnd
                        )
                        viewModel.state.mealTypes.append(newMeal)
                        dashboardViewModel.state.mealTypes.append(newMeal)
                        isAddButtonHidden.toggle()
                    } catch CreateMealTypeResult.emptyName {
                        alertTitle = "Zadejte jméno jídla"
                        showingAlert.toggle()
                    } catch CreateMealTypeResult.invalidName {
                        alertTitle = "Jméno jídla musí být unikátní"
                        showingAlert.toggle()
                    } catch CreateMealTypeResult.invalidTime {
                        alertTitle = "Zadaný čas, se překrývá s jinou kategorií jídla"
                        showingAlert.toggle()
                    } catch {
                        alertTitle = "Nastala nečekaná chyba"
                        showingAlert.toggle()
                    }
                } label: {
                    Text("Vytvořit")
                        .padding()
                        .frame(maxWidth: .infinity)
                }
                .alert(isPresented: $showingAlert) {
                    Alert(
                        title: Text(alertTitle),
                        dismissButton: Alert.Button.default(Text("Dobrá"))
                    )
                }
                .foregroundColor(.white)
                .background(.green)
                .frame(maxWidth: .infinity)
                .padding(.top, -10)
                .font(.system(size: 20, weight: .bold))
            }
            .cornerRadius(10)
        }
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
